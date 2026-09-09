package com.example.appcloner

import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/**
 * App Cloner (Shortcut Edition)
 *
 * Lists every launchable app on the device and, on tap, creates a
 * pinned home-screen shortcut that launches that app directly.
 *
 * Note: this creates a shortcut/duplicate icon to an existing app.
 * It does not create a separate sandboxed "clone" instance of the
 * app (that requires OS-level virtualization and is out of scope).
 */
class MainActivity : AppCompatActivity() {

    private data class AppInfo(val label: String, val packageName: String)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val pm = packageManager
        val launcherIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val resolvedApps = pm.queryIntentActivities(launcherIntent, 0)
            .map { AppInfo(it.loadLabel(pm).toString(), it.activityInfo.packageName) }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }

        val listView = findViewById<ListView>(R.id.appListView)
        listView.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_list_item_1,
            resolvedApps.map { it.label }
        )

        listView.setOnItemClickListener { _, _, position, _ ->
            createHomeScreenShortcut(resolvedApps[position])
        }
    }

    private fun createHomeScreenShortcut(app: AppInfo) {
        val launchIntent = packageManager.getLaunchIntentForPackage(app.packageName)
        if (launchIntent == null) {
            Toast.makeText(this, "Can't launch ${app.label}", Toast.LENGTH_SHORT).show()
            return
        }
        launchIntent.action = Intent.ACTION_MAIN
        launchIntent.addCategory(Intent.CATEGORY_LAUNCHER)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val shortcutManager = getSystemService(ShortcutManager::class.java)
            if (shortcutManager != null && shortcutManager.isRequestPinShortcutSupported) {
                val icon = try {
                    Icon.createWithBitmap(drawableToBitmap(app.packageName))
                } catch (e: Exception) {
                    Icon.createWithResource(this, R.drawable.ic_launcher)
                }

                val shortcut = ShortcutInfo.Builder(this, "clone_${app.packageName}")
                    .setShortLabel(app.label.take(10))
                    .setLongLabel(app.label)
                    .setIcon(icon)
                    .setIntent(launchIntent)
                    .build()

                shortcutManager.requestPinShortcut(shortcut, null)
            } else {
                Toast.makeText(
                    this,
                    "Pinned shortcuts not supported on this launcher",
                    Toast.LENGTH_LONG
                ).show()
                return
            }
        } else {
            @Suppress("DEPRECATION")
            val addIntent = Intent("com.android.launcher.action.INSTALL_SHORTCUT").apply {
                putExtra(Intent.EXTRA_SHORTCUT_INTENT, launchIntent)
                putExtra(Intent.EXTRA_SHORTCUT_NAME, app.label)
                putExtra(
                    Intent.EXTRA_SHORTCUT_ICON_RESOURCE,
                    Intent.ShortcutIconResource.fromContext(this, R.drawable.ic_launcher)
                )
            }
            sendBroadcast(addIntent)
        }

        Toast.makeText(this, "Shortcut requested for ${app.label}", Toast.LENGTH_SHORT).show()
    }

    private fun drawableToBitmap(packageName: String): Bitmap {
        val drawable = packageManager.getApplicationIcon(packageName)
        val width = drawable.intrinsicWidth.coerceAtLeast(1)
        val height = drawable.intrinsicHeight.coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }
}
