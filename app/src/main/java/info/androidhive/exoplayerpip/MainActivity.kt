package info.androidhive.exoplayerpip

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import info.androidhive.exoplayerpip.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private val binding by lazy(LazyThreadSafetyMode.NONE) {
        ActivityMainBinding.inflate(layoutInflater)
    }

    private var mediaUrlLandscape: String =
        "https://firebasestorage.googleapis.com/v0/b/project-8525323942962534560.appspot.com/o/samples%2FBig%20Buck%20Bunny%2060fps%204K%20-%20Official%20Blender%20Foundation%20Short%20Film.mp4?alt=media&token=351ab76e-6e1f-43eb-b868-0a060277a338"

    private var mediaUrlPortrait: String =
        "https://firebasestorage.googleapis.com/v0/b/project-8525323942962534560.appspot.com/o/samples%2F15365448-hd_1080_1920_30fps.mp4?alt=media&token=4e2bc0e5-42f9-412c-9681-df20aa00599d"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        binding.content.btnVideoLandscape.setOnClickListener {
            playVideo(mediaUrlLandscape)
        }

        binding.content.btnVideoPortrait.setOnClickListener {
            playVideo(mediaUrlPortrait)
        }
    }

    private fun playVideo(url: String) {
        startActivity(Intent(this, PlayerActivity::class.java).apply {
            putExtra("url", url)
        })
    }
}