package com.omarea.vboot

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.provider.Settings
import android.support.design.widget.Snackbar
import android.support.v4.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.omarea.shared.*
import com.omarea.shell.Files
import com.omarea.shell.Platform
import com.omarea.shell.Props
import com.omarea.shell.SuDo
import com.omarea.ui.ProgressBarDialog
import kotlinx.android.synthetic.main.layout_home.*
import java.io.File


class FragmentHome : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.layout_home, container, false)
    }

    private var myHandler = Handler()
    private lateinit var globalSPF: SharedPreferences
    private fun showMsg(msg: String) {
        this.view?.let { Snackbar.make(it, msg, Snackbar.LENGTH_LONG).show() }
    }

    private lateinit var spf: SharedPreferences
    private var modeList = ModeList()
    @SuppressLint("ApplySharedPref")
    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        globalSPF = context!!.getSharedPreferences(SpfConfig.GLOBAL_SPF, Context.MODE_PRIVATE)

        if (Platform().dynamicSupport(context!!) || File(Consts.POWER_CFG_PATH).exists()) {
            powermode_toggles.visibility = View.VISIBLE
        }

        btn_powersave.setOnClickListener {
            installConfig(modeList.POWERSAVE, getString(R.string.power_change_powersave))
        }
        btn_defaultmode.setOnClickListener {
            installConfig(modeList.DEFAULT, getString(R.string.power_change_default))
        }
        btn_gamemode.setOnClickListener {
            installConfig(modeList.PERFORMANCE, getString(R.string.power_change_game))
        }
        btn_fastmode.setOnClickListener {
            installConfig(modeList.FAST, getString(R.string.power_chagne_fast))
        }
        home_hide_in_recents.setOnCheckedChangeListener({ _, checked ->
            globalSPF.edit().putBoolean(SpfConfig.GLOBAL_SPF_AUTO_REMOVE_RECENT, checked).commit()
        })
        home_app_nightmode.setOnCheckedChangeListener({ _, checked ->
            if (globalSPF.getBoolean(SpfConfig.GLOBAL_SPF_NIGHT_MODE, false) == checked) {
                return@setOnCheckedChangeListener
            }
            globalSPF.edit().putBoolean(SpfConfig.GLOBAL_SPF_NIGHT_MODE, checked).commit()
            activity!!.recreate()
        })

        spf = context!!.getSharedPreferences(SpfConfig.GLOBAL_SPF, Context.MODE_PRIVATE)

        if (!Platform().dynamicSupport(context!!) && !File(Consts.POWER_CFG_PATH).exists()) {
            settings_dynamic.isEnabled = false
            settings_dynamic.isChecked = false
            spf.edit().putBoolean(SpfConfig.GLOBAL_SPF_DYNAMIC_CPU, false).commit()
        }

        vbootservice_state.setOnClickListener {
            if (AccessibleServiceHelper().serviceIsRunning(context!!)) {
                try {
                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    startActivity(intent)
                } catch (ex: Exception) {

                }
                return@setOnClickListener
            }
            val dialog = ProgressBarDialog(context!!)
            dialog.showDialog("尝试使用ROOT权限开启服务...")
            Thread(Runnable {
                if (!AccessibleServiceHelper().startServiceUseRoot(context!!)) {
                    try {
                        myHandler.post {
                            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                            startActivity(intent)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    } finally {
                        myHandler.post {
                            dialog.hideDialog()
                        }
                    }
                } else {
                    myHandler.post {
                        dialog.hideDialog()
                        val serviceState = AccessibleServiceHelper().serviceIsRunning(context!!)
                        vbootserviceSettings!!.visibility = if (serviceState) View.VISIBLE else View.GONE
                        vbootservice_state.text = if (serviceState) context!!.getString(R.string.accessibility_running) else context!!.getString(R.string.accessibility_stoped)
                    }
                }
            }).start()
        }

        settings_delaystart.setOnCheckedChangeListener({ _, checked ->
            spf.edit().putBoolean(SpfConfig.GLOBAL_SPF_DELAY, checked).commit()
        })
        settings_debugmode.setOnCheckedChangeListener({ _, checked ->
            spf.edit().putBoolean(SpfConfig.GLOBAL_SPF_DEBUG, checked).commit()
        })
        settings_autoinstall.setOnCheckedChangeListener({ _, checked ->
            spf.edit().putBoolean(SpfConfig.GLOBAL_SPF_AUTO_INSTALL, checked).commit()
        })
        settings_autobooster.setOnCheckedChangeListener({ _, checked ->
            spf.edit().putBoolean(SpfConfig.GLOBAL_SPF_AUTO_BOOSTER, checked).commit()
        })
        settings_dynamic.setOnCheckedChangeListener({ _, checked ->
            spf.edit().putBoolean(SpfConfig.GLOBAL_SPF_DYNAMIC_CPU, checked).commit()
        })
        accessbility_notify.setOnCheckedChangeListener({ _, checked ->
            spf.edit().putBoolean(SpfConfig.GLOBAL_SPF_NOTIFY, checked).commit()
        })
        settings_disable_selinux.setOnClickListener {
            if (settings_disable_selinux.isChecked) {
                SuDo(context).execCmdSync(Consts.DisableSELinux)
                myHandler.postDelayed({
                    spf.edit().putBoolean(SpfConfig.GLOBAL_SPF_DISABLE_ENFORCE, true).commit()
                }, 10000)
            } else {
                SuDo(context).execCmdSync(Consts.ResumeSELinux)
                spf.edit().putBoolean(SpfConfig.GLOBAL_SPF_DISABLE_ENFORCE, false).commit()
            }
        }
    }

    override fun onResume() {
        super.onResume()

        home_app_nightmode.isChecked = globalSPF.getBoolean(SpfConfig.GLOBAL_SPF_NIGHT_MODE, false)
        home_hide_in_recents.isChecked = globalSPF.getBoolean(SpfConfig.GLOBAL_SPF_AUTO_REMOVE_RECENT, false)
        setModeState()
        sdfree.text = "SDCard：" + Files.GetDirFreeSizeMB(Environment.getExternalStorageDirectory().absolutePath) + " MB"
        datafree.text = "Data：" + Files.GetDirFreeSizeMB(Environment.getDataDirectory().absolutePath) + " MB"

        val serviceState = AccessibleServiceHelper().serviceIsRunning(context!!)
        vbootserviceSettings!!.visibility = if (serviceState) View.VISIBLE else View.GONE

        vbootservice_state.text = if (serviceState) getString(R.string.accessibility_running) else getString(R.string.accessibility_stoped)

        settings_autoinstall.isChecked = spf.getBoolean(SpfConfig.GLOBAL_SPF_AUTO_INSTALL, false)
        settings_autobooster.isChecked = spf.getBoolean(SpfConfig.GLOBAL_SPF_AUTO_BOOSTER, false)
        settings_dynamic.isChecked = spf.getBoolean(SpfConfig.GLOBAL_SPF_DYNAMIC_CPU, false)
        settings_debugmode.isChecked = spf.getBoolean(SpfConfig.GLOBAL_SPF_DEBUG, false)
        settings_delaystart.isChecked = spf.getBoolean(SpfConfig.GLOBAL_SPF_DELAY, false)
        accessbility_notify.isChecked = spf.getBoolean(SpfConfig.GLOBAL_SPF_NOTIFY, true)
        settings_disable_selinux.isChecked = spf.getBoolean(SpfConfig.GLOBAL_SPF_DISABLE_ENFORCE, true)
    }

    private fun setModeState() {
        btn_powersave.text = "省电"
        btn_defaultmode.text = "均衡"
        btn_gamemode.text = "游戏"
        btn_fastmode.text = "极速"
        val cfg = Props.getProp("vtools.powercfg")
        when (cfg) {
            modeList.BALANCE -> btn_defaultmode.text = "均衡 √"
            modeList.PERFORMANCE -> btn_gamemode.text = "游戏 √"
            modeList.POWERSAVE -> btn_powersave!!.text = "省电 √"
            modeList.FAST -> btn_fastmode!!.text = "极速 √"
        }
    }

    private fun installConfig(action: String, message: String) {
        if (spf.getBoolean(SpfConfig.GLOBAL_SPF_DYNAMIC_CPU, false) && AccessibleServiceHelper().serviceIsRunning(this.context!!)) {
            AlertDialog.Builder(context)
                    .setTitle("")
                    .setMessage("检测到你已经开启“动态响应”，微工具箱将根据你的前台应用，自动调整CPU、GPU性能。\n如果你要更改全局性能，请先关闭“动态响应”！")
                    .setPositiveButton(R.string.btn_confirm, DialogInterface.OnClickListener { dialog, which ->
                    })
                    .show()
                    .create()
        }
        if (File(Consts.POWER_CFG_PATH).exists()) {
            modeList.executePowercfgModeOnce(action, context!!.packageName)
        } else {
            val stringBuilder = StringBuilder()
            stringBuilder.append(String.format(Consts.ToggleMode, action))
            ConfigInstaller().installPowerConfig(context!!, stringBuilder.toString());
        }
        setModeState()
        showMsg(message)
    }
}
