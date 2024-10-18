package info.androidhive.exoplayerpip

import android.app.AppOpsManager
import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Rect
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.annotation.DrawableRes
import androidx.annotation.OptIn
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import info.androidhive.exoplayerpip.databinding.ActivityPlayerBinding


class PlayerActivity : AppCompatActivity() {
    private val binding by lazy(LazyThreadSafetyMode.NONE) {
        ActivityPlayerBinding.inflate(layoutInflater)
    }
    private var player: Player? = null
    private var mediaUrl: String? = null

    private val isPipSupported by lazy {
        packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
    }

    companion object {
        private const val ACTION_PLAYER_CONTROLS = "action_player_controls"
        private const val EXTRA_CONTROL_TYPE = "control_type"
        private const val CONTROL_PLAY_PAUSE = 1
        private const val REQUEST_PLAY_OR_PAUSE = 2
    }

    private var callback: OnBackPressedCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            handleBackPressed()
        }
    }

    // broadcast receiver to receiver actions when pip window action buttons are tapped
    private val broadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent == null || intent.action != ACTION_PLAYER_CONTROLS) {
                return
            }
            when (intent.getIntExtra(EXTRA_CONTROL_TYPE, 0)) {
                // Toggle b/w play and pause
                CONTROL_PLAY_PAUSE -> {
                    if (binding.playerView.player?.isPlaying == true) {
                        binding.playerView.player?.pause()
                    } else {
                        binding.playerView.player?.play()
                    }

                    // update the pip window actions after player is paused or resumed
                    updatePictureInPictureParams()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        mediaUrl = intent.extras?.getString("url")

        if (mediaUrl.isNullOrBlank()) {
            Toast.makeText(this, "Media url is null!", Toast.LENGTH_SHORT).show()
            finish()
        }

        // register the broadcast receiver
        ContextCompat.registerReceiver(
            this, broadcastReceiver, IntentFilter(ACTION_PLAYER_CONTROLS),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    @OptIn(UnstableApi::class)
    private fun initializePlayer() {
        // release the player if the activity is already running in PIP mode
        if (player != null) {
            releasePlayer()
        }

        player = ExoPlayer.Builder(this).build().also { exoPlayer ->
            binding.playerView.player = exoPlayer

            val mediaBuilder = MediaItem.Builder().setUri(mediaUrl)
            val mediaItem = mediaBuilder.build()
            exoPlayer.setMediaItems(listOf(mediaItem))
            exoPlayer.playWhenReady = true
            exoPlayer.prepare()
        }
    }

    private fun enterPipMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            enterPictureInPictureMode(updatePictureInPictureParams())
        } else {
            enterPictureInPictureMode()
        }
    }

    // enter into PIP when home is pressed
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        enterPipMode()
    }

    // Updating picture in picture param
    private fun updatePictureInPictureParams(): PictureInPictureParams {
        val visibleRect = Rect()
        binding.playerView.getGlobalVisibleRect(visibleRect)

        val builder = PictureInPictureParams.Builder()
            .setAspectRatio(Rational(visibleRect.width(), visibleRect.height()))
            .setActions(
                listOf(
                    // Keeping play or pause action based on player state
                    if (binding.playerView.player?.isPlaying == false) {
                        // video is not playing. Keep play action
                        createRemoteAction(
                            R.drawable.ic_play,
                            R.string.play,
                            REQUEST_PLAY_OR_PAUSE,
                            CONTROL_PLAY_PAUSE
                        )
                    } else {
                        // video is playing. Keep pause action
                        createRemoteAction(
                            R.drawable.ic_pause,
                            R.string.pause,
                            REQUEST_PLAY_OR_PAUSE,
                            CONTROL_PLAY_PAUSE
                        )
                    }
                )
            )
            .setSourceRectHint(visibleRect)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // enable auto enter on Android 12
            builder.setAutoEnterEnabled(true)
        }

        val params = builder.build()
        setPictureInPictureParams(params)

        return params
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)

        // Hide player controls when in PIP mode
        binding.playerView.useController = !isInPictureInPictureMode
    }

    /**
     * Enter into PIP when back is pressed and video is playing
     * */
    fun handleBackPressed() {
        if (!isPipSupported || !isPipPermissionEnabled()) {
            finish()
            return
        }

        if (!isInPictureInPictureMode && binding.playerView.player?.isPlaying == true) {
            enterPipMode()
        } else {
            // video is not playing, finish the activity
            finish()
        }
    }

    private fun isPipPermissionEnabled(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager?
        val enabled = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps?.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_PICTURE_IN_PICTURE,
                android.os.Process.myUid(),
                packageName
            ) == AppOpsManager.MODE_ALLOWED
        } else {
            appOps?.checkOpNoThrow(
                AppOpsManager.OPSTR_PICTURE_IN_PICTURE,
                android.os.Process.myUid(),
                packageName
            ) == AppOpsManager.MODE_ALLOWED
        }
        return enabled
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

        // receiving new media url when activity is running in PIP mode
        mediaUrl = intent.extras?.getString("url")
        initializePlayer()
    }

    public override fun onStart() {
        super.onStart()
        callback.remove()
        onBackPressedDispatcher.addCallback(this, callback)
        initializePlayer()
    }

    override fun onPause() {
        super.onPause()
        if (!isInPictureInPictureMode) {
            binding.playerView.onPause()
        }
    }

    override fun onResume() {
        super.onResume()
        binding.playerView.useController = true
    }

    override fun onStop() {
        super.onStop()
        callback.remove()
        releasePlayer()

        // remove the activity from recent tasks as PIP activity won't be
        // removed automatically
        if (packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
            finishAndRemoveTask()
        }
    }

    private fun releasePlayer() {
        player?.release()
        player = null
    }

    // Method to create action button for pip window
    private fun createRemoteAction(
        @DrawableRes iconResId: Int,
        @StringRes titleResId: Int,
        requestCode: Int,
        controlType: Int
    ): RemoteAction {
        return RemoteAction(
            Icon.createWithResource(this, iconResId),
            getString(titleResId),
            getString(titleResId),
            PendingIntent.getBroadcast(
                this,
                requestCode,
                Intent(ACTION_PLAYER_CONTROLS).putExtra(EXTRA_CONTROL_TYPE, controlType),
                PendingIntent.FLAG_IMMUTABLE
            )
        )
    }
}