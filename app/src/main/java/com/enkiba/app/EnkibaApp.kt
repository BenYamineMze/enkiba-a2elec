package com.enkiba.app

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import com.enkiba.app.security.AppLock
import com.enkiba.app.ui.lock.LockActivity
import com.enkiba.app.ui.login.LoginActivity

/** Application : suit le passage au premier plan pour le verrouillage par code PIN. */
class EnkibaApp : Application() {

    private var startedActivities = 0

    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) {
                if (startedActivities++ == 0) AppLock.onForeground(this@EnkibaApp, SystemClock.elapsedRealtime())
            }

            override fun onActivityStopped(activity: Activity) {
                if (--startedActivities == 0) AppLock.onBackground(SystemClock.elapsedRealtime())
            }

            override fun onActivityResumed(activity: Activity) {
                // La connexion n'a pas besoin du code : elle redirige vers l'accueil, qui sera verrouillé
                if (AppLock.screenShown || activity is LockActivity || activity is LoginActivity || !AppLock.isLocked(activity)) return
                AppLock.screenShown = true
                activity.startActivity(Intent(activity, LockActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION))
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
    }
}
