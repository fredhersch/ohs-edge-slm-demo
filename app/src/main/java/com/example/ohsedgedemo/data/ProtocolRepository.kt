package com.example.ohsedgedemo.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader

data class ProtocolUnit(
    val id: String,
    val domain: String,
    val topic: String,
    val sourceManual: String,
    val sourceCode: String,
    val verbatimExcerpt: String,
    val entryCriteria: String,
    val dangerSigns: List<String>,
    val mandatedActions: List<String>,
    val negativeBoundaries: List<String>,
    val promptInjectionChunk: String,
    val isCertified: Boolean
)

interface ProtocolRepository {
    val allProtocols: List<ProtocolUnit>
    fun getProtocolById(id: String): ProtocolUnit?
    fun matchProtocolForCase(clinicalCase: ClinicalCase): ProtocolUnit?
    fun searchProtocols(query: String): List<ProtocolUnit>
}

class AndroidProtocolRepository(private val context: Context) : ProtocolRepository {

    private val _protocols = mutableListOf<ProtocolUnit>()
    override val allProtocols: List<ProtocolUnit>
        get() = _protocols

    init {
        loadProtocols()
    }

    private fun loadProtocols() {
        // First try to load from SQLite database in assets
        val dbCopied = ensureDatabaseCopied()
        if (dbCopied) {
            try {
                val dbPath = context.getDatabasePath("protocols.db").absolutePath
                val db = SQLiteDatabase.openDatabase(dbPath, null, SQLiteDatabase.OPEN_READONLY)
                val cursor = db.rawQuery(
                    """
                    SELECT id, domain, topic, source_manual, source_code, verbatim_excerpt,
                           entry_criteria, danger_signs_json, mandated_actions_json,
                           negative_boundaries_json, prompt_injection_chunk, certified
                    FROM protocol_units ORDER BY id ASC
                    """.trimIndent(), null
                )

                while (cursor.moveToNext()) {
                    val dangerSignsList = parseJsonArray(cursor.getString(7))
                    val actionsList = parseJsonArray(cursor.getString(8))
                    val negativesList = parseJsonArray(cursor.getString(9))

                    _protocols.add(
                        ProtocolUnit(
                            id = cursor.getString(0),
                            domain = cursor.getString(1),
                            topic = cursor.getString(2),
                            sourceManual = cursor.getString(3),
                            sourceCode = cursor.getString(4),
                            verbatimExcerpt = cursor.getString(5),
                            entryCriteria = cursor.getString(6),
                            dangerSigns = dangerSignsList,
                            mandatedActions = actionsList,
                            negativeBoundaries = negativesList,
                            promptInjectionChunk = cursor.getString(10),
                            isCertified = cursor.getInt(11) == 1
                        )
                    )
                }
                cursor.close()
                db.close()
                if (_protocols.isNotEmpty()) return
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Fallback: parse JSON directly from assets
        try {
            val jsonStream = context.assets.open("protocols/kenya_moh_mnh_protocols.json")
            val jsonString = InputStreamReader(jsonStream).readText()
            val array = JSONArray(jsonString)

            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val ref = obj.optJSONObject("source_reference")
                val payload = obj.optJSONObject("clinical_payload")

                val dangerSigns = mutableListOf<String>()
                val dsArr = payload?.optJSONArray("danger_signs")
                if (dsArr != null) {
                    for (j in 0 until dsArr.length()) {
                        val item = dsArr.get(j)
                        if (item is JSONObject) dangerSigns.add(item.optString("sign", ""))
                        else dangerSigns.add(item.toString())
                    }
                }

                val actions = mutableListOf<String>()
                val actArr = payload?.optJSONArray("mandated_chw_actions")
                if (actArr != null) {
                    for (j in 0 until actArr.length()) actions.add(actArr.getString(j))
                }

                val negatives = mutableListOf<String>()
                val negArr = payload?.optJSONArray("negative_boundaries")
                if (negArr != null) {
                    for (j in 0 until negArr.length()) {
                        val item = negArr.get(j)
                        if (item is JSONObject) negatives.add(item.optString("prohibition", ""))
                        else negatives.add(item.toString())
                    }
                }

                _protocols.add(
                    ProtocolUnit(
                        id = obj.getString("id"),
                        domain = obj.optString("domain", "02_mnh"),
                        topic = obj.getString("topic"),
                        sourceManual = "${ref?.optString("organization", "Kenya MOH")} • ${ref?.optString("manual", "")}",
                        sourceCode = ref?.optString("page_or_code", "") ?: "",
                        verbatimExcerpt = ref?.optString("verbatim_page_excerpt", "") ?: "",
                        entryCriteria = payload?.optString("entry_criteria", "") ?: "",
                        dangerSigns = dangerSigns,
                        mandatedActions = actions,
                        negativeBoundaries = negatives,
                        promptInjectionChunk = obj.optString("prompt_injection_chunk", ""),
                        isCertified = true
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun ensureDatabaseCopied(): Boolean {
        return try {
            val dbFile = context.getDatabasePath("protocols.db")
            if (!dbFile.exists() || dbFile.length() == 0L) {
                dbFile.parentFile?.mkdirs()
                context.assets.open("protocols/protocols.db").use { input ->
                    FileOutputStream(dbFile).use { output ->
                        input.copyTo(output)
                    }
                }
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun parseJsonArray(jsonStr: String?): List<String> {
        if (jsonStr.isNullOrBlank()) return emptyList()
        val list = mutableListOf<String>()
        return try {
            val arr = JSONArray(jsonStr)
            for (i in 0 until arr.length()) {
                val item = arr.get(i)
                if (item is JSONObject) {
                    list.add(item.optString("sign", item.optString("prohibition", item.toString())))
                } else {
                    list.add(item.toString())
                }
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    override fun getProtocolById(id: String): ProtocolUnit? {
        return _protocols.firstOrNull { it.id.equals(id, ignoreCase = true) }
    }

    override fun matchProtocolForCase(clinicalCase: ClinicalCase): ProtocolUnit? {
        // Direct deterministic mapping based on clinical condition & vitals
        val cond = clinicalCase.activeCondition.lowercase()
        val markdown = clinicalCase.tokempicMarkdown.lowercase()

        return when {
            cond.contains("hypertension") || markdown.contains("148/96") || markdown.contains("pre-eclampsia") ->
                getProtocolById("KENYA-MOH-MNH-02")
            cond.contains("bleeding") || cond.contains("antepartum") || cond.contains("hemorrhage") ->
                getProtocolById("KENYA-MOH-MNH-03")
            cond.contains("anemia") || markdown.contains("hb") ->
                getProtocolById("KENYA-MOH-MNH-04")
            cond.contains("omphalitis") || markdown.contains("cord") ->
                getProtocolById("KENYA-MOH-MNH-18") ?: getProtocolById("KENYA-MOH-MNH-15")
            cond.contains("malaria") && !markdown.contains("resolved") ->
                getProtocolById("KENYA-MOH-MNH-05")
            cond.contains("jaundice") ->
                getProtocolById("KENYA-MOH-MNH-19")
            cond.contains("birth plan") || markdown.contains("due") ->
                getProtocolById("KENYA-MOH-MNH-07")
            else ->
                _protocols.firstOrNull()
        }
    }

    override fun searchProtocols(query: String): List<ProtocolUnit> {
        if (query.isBlank()) return _protocols
        val q = query.lowercase()
        return _protocols.filter {
            it.topic.lowercase().contains(q) ||
            it.id.lowercase().contains(q) ||
            it.entryCriteria.lowercase().contains(q) ||
            it.dangerSigns.any { ds -> ds.lowercase().contains(q) } ||
            it.negativeBoundaries.any { nb -> nb.lowercase().contains(q) }
        }
    }
}
