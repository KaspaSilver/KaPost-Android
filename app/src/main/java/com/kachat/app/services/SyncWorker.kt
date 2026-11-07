package com.kachat.app.services

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.kachat.app.repository.GroupRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Periodic background catch-up for GROUP chat only ([GroupRepository.syncGroups]) — groups are the
 * one notification surface remote push does NOT cover (the push registration is the LegacyV1
 * shape with no `watched_group_ids`), so this ~15-minute WorkManager cadence is their sole
 * closed-app delivery path, including the group-invite (`gctl_root`) and group-message
 * notifications syncGroups posts itself. Both `gcomm`/`gctl` payloads are self-stash transactions
 * on a queryable identity address, now indexed (`group-messages/by-blinded-group-id`,
 * `group-control/by-sender`), so they fit this short-lived catch-up shape.
 *
 * 1:1 chat, broadcasts, and KaPosts are deliberately NOT synced here: FCM push (see
 * PushRegistrationManager) is the only background delivery path for those — a background poller
 * would mask push failures. Broadcast messages additionally have no queryable per-cursor history
 * to catch up on, only a live block subscription, which doesn't fit a periodic job anyway.
 *
 * A scheduled KaPost only this phone holds is the one KaPosts exception: it goes out here if its
 * time has come (iOS BackgroundTaskManager.swift `handleBackgroundFetch`) - it is the author's own
 * post, not a fetch. KaPost cancels this worker at launch (KaChatApplication), so the post's own
 * one-time job ([KaPostsScheduledReminderWorker]) is what actually sends it in the background.
 */
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val groupRepository: GroupRepository,
    private val kaPostsScheduledStore: KaPostsScheduledStore,
    private val walletManager: WalletManager,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        // Before the sync, and whatever it does; signed out, sendDueLocally does nothing.
        try {
            kaPostsScheduledStore.sendDueLocally()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("SyncWorker", "Scheduled posts: background send failed", e)
        }
        // Signed out (Log Out, or Delete with other accounts left): no group sync for whichever
        // saved account is left over, as the KaPosts poller and push do nothing then.
        if (walletManager.isLoggedOut) return Result.success()
        return try {
            groupRepository.syncGroups()
            Result.success()
        } catch (e: Exception) {
            Log.w("SyncWorker", "Periodic group sync failed, will retry next cycle", e)
            Result.retry()
        }
    }

    companion object {
        const val WORK_NAME = "kachat_periodic_sync"
    }
}
