package com.enkiba.app.security

import android.content.Context
import com.enkiba.app.utils.SessionManager

/** Résultat d'une saisie du code PIN. */
sealed interface PinResult {
    data object Ok : PinResult
    data class Wrong(val remaining: Int) : PinResult
    /** Trop d'erreurs : retour à la connexion par mot de passe. */
    data object TooManyAttempts : PinResult
}

/**
 * Verrouillage de l'application par code PIN (facultatif, activé par chaque utilisateur).
 * L'app se verrouille à l'ouverture et quand elle revient au premier plan après
 * [TIMEOUT_MS] en arrière-plan. Le code est effacé avec la session (déconnexion).
 */
object AppLock {

    const val PIN_LENGTH = 4
    const val MAX_FAILURES = 5
    const val TIMEOUT_MS = 2 * 60_000L

    @Volatile
    private var locked = false

    /** Démarrage du processus pas encore évalué (lecture de la session au premier écran). */
    @Volatile
    private var coldStartPending = true

    /** Écran de verrouillage déjà ouvert : évite d'en empiler plusieurs. */
    @Volatile
    var screenShown = false

    private var backgroundSince: Long? = null

    fun isEnabled(session: SessionManager) = session.isLoggedIn && session.pinHash != null

    /** Vrai tant que l'écran de verrouillage doit s'afficher ; au démarrage, verrouillé si un code existe. */
    fun isLocked(context: Context): Boolean {
        if (coldStartPending) {
            coldStartPending = false
            locked = isEnabled(SessionManager(context))
        }
        return locked
    }

    fun onBackground(now: Long) {
        backgroundSince = now
    }

    fun onForeground(context: Context, now: Long) {
        val since = backgroundSince ?: return
        backgroundSince = null
        if (shouldLock(since, now) && isEnabled(SessionManager(context))) locked = true
    }

    /** Assez longtemps en arrière-plan pour redemander le code (horloge reculée : on verrouille). */
    fun shouldLock(backgroundSince: Long, now: Long) = now - backgroundSince >= TIMEOUT_MS || now < backgroundSince

    fun unlock() {
        coldStartPending = false
        locked = false
    }

    fun enable(session: SessionManager, pin: String) {
        val salt = PinHasher.newSalt()
        session.savePin(PinHasher.hash(pin, salt), salt)
    }

    fun disable(session: SessionManager) = session.clearPin()

    /** Vérifie le code et tient le compte des erreurs (remis à zéro dès qu'un code est bon). */
    fun check(session: SessionManager, pin: String): PinResult {
        val hash = session.pinHash ?: return PinResult.Ok
        val salt = session.pinSalt ?: return PinResult.Ok
        val result = evaluate(PinHasher.matches(pin, salt, hash), session.pinFailures)
        session.pinFailures = if (result is PinResult.Wrong) MAX_FAILURES - result.remaining else 0
        return result
    }

    /** Règle des essais, isolée pour les tests. */
    fun evaluate(matches: Boolean, failuresBefore: Int): PinResult = when {
        matches -> PinResult.Ok
        failuresBefore + 1 >= MAX_FAILURES -> PinResult.TooManyAttempts
        else -> PinResult.Wrong(MAX_FAILURES - failuresBefore - 1)
    }
}
