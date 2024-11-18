package com.kt.apps.video.ui

import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Menu
import android.view.MenuInflater
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.preference.DialogPreference
import androidx.preference.ListPreference
import androidx.preference.ListPreferenceDialogFragmentCompat
import androidx.preference.Preference
import androidx.preference.PreferenceManager
import com.google.android.material.snackbar.Snackbar
import org.schabi.newpipe.BuildConfig
import org.schabi.newpipe.R
import org.schabi.newpipe.databinding.FragmentAboutItubeBinding
import org.schabi.newpipe.util.PermissionHelper

class ITubeAboutFragment : Fragment(R.layout.fragment_about_itube), DialogPreference.TargetFragment {
    private var binding: FragmentAboutItubeBinding? = null
    private val sharedPreferences by lazy { PreferenceManager.getDefaultSharedPreferences(requireContext()) }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
        initPreferences()
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = FragmentAboutItubeBinding.bind(view)
        this.binding = binding
        binding.appInfoSectionSubTextView.text = String.format(
            getString(R.string.version),
            BuildConfig.VERSION_NAME
        )
        updateNewPreference(
            getString(R.string.minimize_on_exit_key),
            sharedPreferences.getString(getString(R.string.minimize_on_exit_key), getString(R.string.minimize_on_exit_value))
        )
        updateNewPreference(
            getString(R.string.itube_play_mode_key),
            sharedPreferences.getString(getString(R.string.itube_play_mode_key), getString(R.string.itube_play_mode_value))
        )
        binding.minimizeOnExitView.setOnClickListener {
            val bundle = bundleOf(
                "key" to getString(R.string.minimize_on_exit_key)
            )
            val fragment = ListPreferenceDialogFragmentCompat()
            fragment.setTargetFragment(this, 1012)
            fragment.arguments = bundle
            fragment.show(parentFragmentManager, "minimize_on_exit")
        }
        binding.playModeSection?.setOnClickListener {
            val bundle = bundleOf(
                "key" to getString(R.string.itube_play_mode_key)
            )
            val fragment = ListPreferenceDialogFragmentCompat()
            fragment.setTargetFragment(this, 1012)
            fragment.arguments = bundle
            fragment.show(parentFragmentManager, "itube_play_mode")
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        val supportActionBar = (activity as? AppCompatActivity)?.supportActionBar
        if (supportActionBar != null) {
            supportActionBar.setDisplayShowTitleEnabled(false)
            supportActionBar.setDisplayHomeAsUpEnabled(false)
            activity?.findViewById<View>(R.id.logo_group)?.visibility = View.GONE
        }
    }

    private fun updateNewPreference(
        key: String,
        newValue: String?
    ) {
        when (key) {
            getString(R.string.minimize_on_exit_key) -> {
                val value = run {
                    val index = resources.getStringArray(R.array.minimize_on_exit_action_key).indexOfFirst {
                        it == newValue
                    }
                    if (index != -1) {
                        resources.getStringArray(R.array.minimize_on_exit_action_description)[index]
                    } else {
                        null
                    }
                }
                binding?.minimizeOnExitSubTextView?.text = String.format(
                    getString(R.string.minimize_on_exit_summary),
                    value
                )
            }
            getString(R.string.itube_play_mode_key) -> {
                val value = run {
                    val index = resources.getStringArray(R.array.itube_play_mode_action_key).indexOfFirst {
                        it == newValue
                    }
                    if (index != -1) {
                        resources.getStringArray(R.array.itube_play_mode_action_description)[index]
                    } else {
                        null
                    }
                }
                binding?.playModeDetailTextView?.text = value
            }
        }
    }

    private val preferences = mutableListOf<Preference>()
    private fun initPreferences() {
        preferences.clear()
        run {
            val listPreference = ListPreference(requireContext())
            val key = getString(R.string.minimize_on_exit_key)
            listPreference.key = key
            listPreference.title = getString(R.string.minimize_on_exit_title)
            listPreference.summary = getString(R.string.minimize_on_exit_summary)
            listPreference.value = sharedPreferences.getString(getString(R.string.minimize_on_exit_key), getString(R.string.minimize_on_exit_value))
            listPreference.entries = resources.getStringArray(R.array.minimize_on_exit_action_description)
            listPreference.entryValues = resources.getStringArray(R.array.minimize_on_exit_action_key)
            listPreference.dialogTitle = getString(R.string.minimize_on_exit_title)
            listPreference.negativeButtonText = getString(R.string.close)
            listPreference.setOnPreferenceChangeListener { preference, newValue ->
                sharedPreferences.edit {
                    putString(preference.key, newValue.toString())
                }
                updateNewPreference(key, newValue.toString())

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && getString(R.string.minimize_on_exit_key) == key) {
                    if (newValue != null && newValue == getString(R.string.minimize_on_exit_popup_key) && !Settings.canDrawOverlays(context)) {
                        Snackbar.make(
                            requireView(), R.string.permission_display_over_apps,
                            Snackbar.LENGTH_LONG
                        )
                            .setAction(R.string.settings) { PermissionHelper.checkSystemAlertWindowPermission(context) }
                            .show()
                    }
                }
                true
            }
            preferences.add(listPreference)
        }

        run {
            val listPreference = ListPreference(requireContext())
            val key = getString(R.string.itube_play_mode_key)
            listPreference.key = key
            listPreference.title = getString(R.string.itube_play_mode_title)
            listPreference.summary = getString(R.string.itube_play_mode_description_windowing)
            listPreference.value = sharedPreferences.getString(getString(R.string.itube_play_mode_key), getString(R.string.itube_play_mode_value))
            listPreference.entries = resources.getStringArray(R.array.itube_play_mode_action_description)
            listPreference.entryValues = resources.getStringArray(R.array.itube_play_mode_action_key)
            listPreference.dialogTitle = getString(R.string.itube_play_mode_title)
            listPreference.negativeButtonText = getString(R.string.close)
            listPreference.setOnPreferenceChangeListener { preference, newValue ->
                sharedPreferences.edit {
                    putString(preference.key, newValue.toString())
                }
                updateNewPreference(key, newValue.toString())
                true
            }
            preferences.add(listPreference)
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T : Preference?> findPreference(key: CharSequence): T? {
        return preferences.firstOrNull { key == it.key } as? T
    }
}
