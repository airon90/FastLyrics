package io.github.teccheck.fastlyrics.ui.fastlyrics

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.squareup.picasso.Picasso
import dev.forkhandles.result4k.Failure
import dev.forkhandles.result4k.Success
import io.github.teccheck.fastlyrics.R
import io.github.teccheck.fastlyrics.Settings
import io.github.teccheck.fastlyrics.databinding.FragmentFastLyricsBinding
import io.github.teccheck.fastlyrics.exceptions.LyricsApiException
import io.github.teccheck.fastlyrics.exceptions.LyricsNotFoundException
import io.github.teccheck.fastlyrics.exceptions.NetworkException
import io.github.teccheck.fastlyrics.exceptions.NoMusicPlayingException
import io.github.teccheck.fastlyrics.exceptions.ParseException
import io.github.teccheck.fastlyrics.service.DummyNotificationListenerService
import io.github.teccheck.fastlyrics.utils.Utils.copyToClipboard


class FastLyricsFragment : Fragment() {

    private lateinit var lyricsViewModel: FastLyricsViewModel
    private var _binding: FragmentFastLyricsBinding? = null

    // This property is only valid between onCreateView and onDestroyView.
    private val binding get() = _binding!!

    private val menuProvider = object : MenuProvider {
        override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
            menuInflater.inflate(R.menu.fragment_lyrics_menu, menu)
        }

        override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
            if (menuItem.itemId == R.id.menu_copy_lyrics_to_clipboard) {
                lyricsViewModel.songWithLyrics.value?.let {
                    if (it is Success) {
                        copyToClipboard(getString(R.string.lyrics_clipboard_label), it.value.lyrics)
                    }
                }

                return true
            }
            return false
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        lyricsViewModel = ViewModelProvider(this)[FastLyricsViewModel::class.java]
        _binding = FragmentFastLyricsBinding.inflate(inflater, container, false)

        binding.lyricsView.container.visibility = View.GONE

        lyricsViewModel.songMeta.observe(viewLifecycleOwner) { result ->
            when (result) {
                is Success -> {
                    binding.header.container.visibility = View.VISIBLE
                    binding.errorView.container.visibility = View.GONE
                    binding.header.textSongTitle.text = result.value.title
                    binding.header.textSongArtist.text = result.value.artist
                    binding.header.imageSongArt.setImageBitmap(result.value.art)
                }

                is Failure -> {
                    displayError(result.reason)
                }
            }
        }

        lyricsViewModel.songWithLyrics.observe(viewLifecycleOwner) { result ->
            binding.refreshLayout.isRefreshing = false

            when (result) {
                is Success -> {
                    binding.header.container.visibility = View.VISIBLE
                    binding.lyricsView.container.visibility = View.VISIBLE
                    binding.errorView.container.visibility = View.GONE

                    binding.header.textSongTitle.text = result.value.title
                    binding.header.textSongArtist.text = result.value.artist
                    binding.lyricsView.textLyrics.text = result.value.lyrics
                    Picasso.get().load(result.value.artUrl).into(binding.header.imageSongArt)

                    binding.lyricsView.footer.setOnClickListener { openLink(result.value.sourceUrl) }
                }

                is Failure -> {
                    displayError(result.reason)
                }
            }
        }

        binding.refreshLayout.setColorSchemeResources(
            R.color.theme_primary, R.color.theme_secondary
        )
        binding.refreshLayout.setOnRefreshListener { loadLyricsForCurrentSong() }

        val notificationAccess =
            context?.let { DummyNotificationListenerService.canAccessNotifications(it) } ?: false

        if (notificationAccess) {
            loadLyricsForCurrentSong()
            context?.let {
                lyricsViewModel.setupSongMetaListener(it)
                lyricsViewModel.autoRefresh = Settings(it).getIsAutoRefreshEnabled()
            }
        }

        return binding.root
    }

    override fun onResume() {
        super.onResume()
        enableMenu()
    }

    override fun onPause() {
        super.onPause()
        disableMenu()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun loadLyricsForCurrentSong() {
        context?.let {
            binding.refreshLayout.isRefreshing = true
            val success = lyricsViewModel.loadLyricsForCurrentSong(it)

            if (!success) binding.refreshLayout.isRefreshing = false
        }
    }

    private fun displayError(exception: LyricsApiException) {
        binding.lyricsView.container.visibility = View.GONE
        binding.errorView.container.visibility = View.VISIBLE

        binding.errorView.errorText.text = getErrorTextForApiException(exception)
        binding.errorView.errorIcon.setImageDrawable(getErrorIconForApiException(exception))

        val headerVisibility = if (exception is NoMusicPlayingException) {
            View.GONE
        } else {
            View.VISIBLE
        }

        binding.header.container.visibility = headerVisibility
    }

    private fun getErrorTextForApiException(exception: LyricsApiException): String =
        when (exception) {
            is LyricsNotFoundException -> getString(R.string.lyrics_not_found)
            is NetworkException -> getString(R.string.lyrics_network_exception)
            is ParseException -> getString(R.string.lyrics_parse_exception)
            is NoMusicPlayingException -> getString(R.string.no_song_playing)
            else -> getString(R.string.lyrics_unknown_error)
        }

    private fun getErrorIconForApiException(exception: LyricsApiException) = when (exception) {
        is NoMusicPlayingException -> ResourcesCompat.getDrawable(
            resources, R.drawable.outline_music_off_24, null
        )

        else -> ResourcesCompat.getDrawable(
            resources, R.drawable.baseline_error_outline_24, null
        )
    }

    private fun openLink(link: String) = startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link)))

    private fun enableMenu() {
        if (activity is ComponentActivity) {
            (activity as ComponentActivity).addMenuProvider(menuProvider)
        }
    }

    private fun disableMenu() {
        if (activity is ComponentActivity) {
            (activity as ComponentActivity).removeMenuProvider(menuProvider)
        }
    }

    companion object {
        private const val TAG = "FastLyricsFragment"
    }
}