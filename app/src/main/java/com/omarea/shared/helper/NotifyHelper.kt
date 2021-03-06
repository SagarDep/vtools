package com.omarea.shared.helper

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.support.v4.app.NotificationCompat
import android.util.Log
import android.widget.RemoteViews
import com.omarea.shared.ModeList
import com.omarea.shell.KeepShellSync
import com.omarea.shell.KernelProrp
import com.omarea.shell.RootFile
import com.omarea.shell.SysUtils
import com.omarea.vtools.ActivityQuickSwitchMode
import com.omarea.vtools.R


/**
 * 用于在通知栏显示通知
 * Created by Hello on 2018/01/23.
 */

internal class NotifyHelper(private var context: Context, notify: Boolean = false) : ModeList() {
    private var showNofity: Boolean = false
    private var notification: Notification? = null
    private var notificationManager: NotificationManager? = null

    private fun getAppName(packageName: String): CharSequence? {
        try {
            return context.packageManager.getPackageInfo(packageName, 0).applicationInfo.loadLabel(context.packageManager)
        } catch (ex: Exception) {
            return packageName
        }
    }

    private var batteryTemp = "?°C"
    private var batteryCapacity = ""
    private var batteryStatus = "0"
    private var batteryIO = "0"

    private var batteryUnit = Int.MIN_VALUE
    private var batterySensor: String? = "init"
    private fun getBatteryUnit(): Int {
        if (batteryUnit == Int.MIN_VALUE) {
            val full = KernelProrp.getProp("/sys/class/power_supply/battery/charge_full_design")
            if (full.length >= 4) {
                return full.length - 4
            }
            batteryUnit = -1
        }
        return batteryUnit
    }

    private fun getCapacity(): String {
        if (batteryCapacity.isEmpty() || batteryCapacity == "%?") {
            return ""
        } else {
            return batteryCapacity
        }
        // return KernelProrp.getProp("/sys/class/power_supply/battery/capacity") + "%"
    }

    private fun getBatteryIcon(capacity: Int): Int {
        if (capacity < 20)
            return R.drawable.b_0
        if (capacity < 30)
            return R.drawable.b_1
        if (capacity < 70)
            return R.drawable.b_2
        return R.drawable.b_3
    }

    private fun getBatterySensor(): String? {
        if (batterySensor == "init") {
            batterySensor = SysUtils.executeCommandWithOutput(false, "for sensor in /sys/class/thermal/*; do\n" +
                    "\ttype=\"\$(cat \$sensor/type)\"\n" +
                    "\tif [[ \"\$type\" = \"battery\" && -f \"\$sensor/temp\" ]]; then\n" +
                    "\t\techo \"\$sensor/temp\";\n" +
                    "\t\texit 0;\n" +
                    "\tfi;\n" +
                    "done;")
            if (batterySensor != null) {
                batterySensor = batterySensor!!.trim()
            } else {
                batterySensor = null
            }
        }
        return batterySensor
    }

    private fun updateBatteryInfo() {
        val batteryInfo = KeepShellSync.doCmdSync("dumpsys battery")
        val batteryInfos = batteryInfo.split("\n")

        // 由于部分手机相同名称的参数重复出现，并且值不同，为了避免这种情况，加个额外处理，同名参数只读一次
        var levelReaded = false
        var tempReaded = false
        var statusReaded = false

        for (item in batteryInfos) {
            val info = item.trim()
            val index = info.indexOf(":")
            if (index > -1 && index < info.length) {
                val value = info.substring(info.indexOf(":") + 1).trim()
                if (info.startsWith("status")) {
                    if (!statusReaded) {
                        batteryStatus = value
                        statusReaded = true
                    } else {
                        continue
                    }
                } else if (info.startsWith("level")) {
                    if (!levelReaded) {
                        batteryCapacity = value
                        levelReaded = true
                    } else continue
                } else if (info.startsWith("temperature")) {
                    if (!tempReaded) {
                        tempReaded = true
                        batteryTemp = value
                    } else continue
                }
            }
        }
    }

    private fun getBatteryTemp(): String {
        if (batteryTemp == "?°C" || batteryTemp.isEmpty()) {
            return "?°C"
        }
        try {
            return (batteryTemp.toInt() / 10.0).toString() + "°C"
        } catch (ex: Exception) {
            return "?°C"
        }
        /*
        try {
            val sensor = getBatterySensor()
            if (sensor != null && !sensor.isNullOrEmpty()) {
                val temp = KernelProrp.getProp(sensor)
                if (temp == null || (temp.length < 4)) {
                    return "? °C"
                }
                return temp.substring(0, temp.length - 3) + "°C"
            } else {
                return "? °C"
            }
        } catch (ex: Exception) {
            Log.e("NotifyHelper", "getBatteryTemp, " + ex.message)
            return "? °C"
        }
        */
    }

    var path: String? = null
    private fun getBatteryIO(): String? {
        if (path == null) {
            if (RootFile.itemExists("/sys/class/power_supply/battery/current_now")) {
                path = "/sys/class/power_supply/battery/current_now"
            } else if (RootFile.itemExists("/sys/class/power_supply/battery/BatteryAverageCurrent")) {
                path = "/sys/class/power_supply/battery/BatteryAverageCurrent"
            } else {
                path = ""
            }
        }

        if (path.isNullOrEmpty()) {
            return "? mA"
        }

        var io = KernelProrp.getProp(path!!)
        if (io.isEmpty()) {
            return "? mA"
        }
        try {
            val unit = getBatteryUnit()
            var start = ""
            if (io.startsWith("+")) {
                start = "+"
                io = io.substring(1, io.length)
            } else if (io.startsWith("-")) {
                start = "-"
                io = io.substring(1, io.length)
            } else {
                start = ""
            }
            if (unit != -1 && io.length > unit) {
                return start + io.substring(0, io.length - unit) + "mA"
            } else if (io.length <= 4) {
                return start + io + "mA"
            } else if (io.length >= 8) {
                return start + io.substring(0, io.length - 6) + "mA"
            } else if (io.length >= 5) {
                return start + io.substring(0, io.length - 3) + "mA"
            }
            return start + io
        } catch (ex: Exception) {
            Log.e("parse-battery-io", "$io -> mAh" + ex.message)
            return "? mA"
        }
    }

    //显示通知
    internal fun notify() {
        updateNotic()
    }

    private fun updateNotic() {
        try {
            var currentMode = getCurrentPowerMode()
            if (currentMode == null || currentMode.length == 0) {
                currentMode = ""
            }

            var currentApp = getCurrentPowermodeApp()
            if (currentApp == null || currentApp.length == 0) {
                currentApp = context.packageName
            }

            notifyPowerModeChange(currentApp!!, currentMode)
        } catch (ex: Exception) {
            Log.e("NotifyHelper", ex.localizedMessage)
        }
    }

    private fun notifyPowerModeChange(packageName: String, mode: String) {
        if (!showNofity) {
            return
        }
        var batteryImage: Bitmap? = null
        var batteryIO = getBatteryIO()
        var batteryTemp = ""
        var capacity = ""
        var modeImage = BitmapFactory.decodeResource(context.resources, getModImage(mode))
        try {
            updateBatteryInfo()
            batteryIO = getBatteryIO()
            batteryTemp = getBatteryTemp()
            capacity = getCapacity()
            modeImage = BitmapFactory.decodeResource(context.resources, getModImage(mode))
            if (batteryStatus == "2") {
                batteryImage = BitmapFactory.decodeResource(context.resources, R.drawable.b_4)
            } else if (!capacity.isEmpty()) {
                batteryImage = BitmapFactory.decodeResource(context.resources, getBatteryIcon(capacity.replace("%", "").toInt()))
            }
        } catch (ex: Exception) {
            Log.e("NotifyHelper", ex.message)
        }

        val remoteViews = RemoteViews(context.packageName, R.layout.notify0)
        remoteViews.setTextViewText(R.id.notify_title, getAppName(packageName))
        remoteViews.setTextViewText(R.id.notify_text, getModName(mode))
        remoteViews.setTextViewText(R.id.notify_battery_text, batteryIO + " " + capacity + "% " + batteryTemp)
        if (modeImage != null) {
            remoteViews.setImageViewBitmap(R.id.notify_mode, modeImage)
        }
        if (batteryImage != null) {
            remoteViews.setImageViewBitmap(R.id.notify_battery_icon, batteryImage)
        }

        val intent = PendingIntent.getActivity(
                context,
                0,
                Intent(context, ActivityQuickSwitchMode::class.java).putExtra("packageName", packageName),
                PendingIntent.FLAG_UPDATE_CURRENT
        )

        val icon = getModIcon(mode)
        notificationManager = context.getSystemService(Service.NOTIFICATION_SERVICE) as NotificationManager
        var builder: NotificationCompat.Builder? = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (notificationManager!!.getNotificationChannel("vtool-long-time") == null) {
                notificationManager!!.createNotificationChannel(NotificationChannel("vtool-long-time", "常驻通知", NotificationManager.IMPORTANCE_LOW))
            }
            builder = NotificationCompat.Builder(context, "vtool-long-time")
        } else {
            builder = NotificationCompat.Builder(context)
        }
        notification =
                builder.setSmallIcon(if (false) R.drawable.fanbox else icon)
                        .setContent(remoteViews)
                        .setWhen(System.currentTimeMillis())
                        .setAutoCancel(true)
                        //.setDefaults(Notification.DEFAULT_SOUND)
                        .setContentIntent(intent)
                        .build()

        notification!!.flags = Notification.FLAG_NO_CLEAR or Notification.FLAG_ONGOING_EVENT
        notificationManager?.notify(0x100, notification)
    }

    //隐藏通知
    internal fun hideNotify() {
        if (notification != null) {
            notificationManager?.cancel(0x100)
            notification = null
            notificationManager = null
        }
    }

    internal fun setNotify(show: Boolean) {
        this.showNofity = show
        if (!show) {
            hideNotify()
        } else {
            notify()
        }
    }

    init {
        showNofity = notify
    }
}
