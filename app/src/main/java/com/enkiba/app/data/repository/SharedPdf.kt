package com.enkiba.app.data.repository

import java.io.File

/** PDF prêt à partager : fichier, numéro du client (peut être vide) et message d'accompagnement. */
data class SharedPdf(val file: File, val phone: String?, val message: String)
