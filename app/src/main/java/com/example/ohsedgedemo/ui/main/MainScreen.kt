package com.example.ohsedgedemo.ui.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import android.app.Application
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import com.example.ohsedgedemo.data.ClinicalCase
import com.example.ohsedgedemo.data.DefaultDataRepository
import com.example.ohsedgedemo.data.ProtocolUnit

@Composable
fun MainScreen(
    onItemClick: (NavKey) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MainScreenViewModel = viewModel {
        val app = checkNotNull(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]) as Application
        MainScreenViewModel(app, DefaultDataRepository())
    },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. App Header & Offline Badge
        HeaderSection()

        // Live Model Status Chip
        Surface(
            color = if (state.isLiveModelReady) Color(0xFFE8F5E9) else Color(0xFFF1F5F9),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = state.modelStatusMessage,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = if (state.isLiveModelReady) Color(0xFF1B5E20) else Color(0xFF475569),
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
            )
        }

        // 2. Patient / Case Switcher Tabs
        CaseSelector(
            cases = state.allCases,
            selectedCaseId = state.selectedCase.id,
            onSelect = { viewModel.selectCase(it) }
        )

        // 3. Clinical Patient Card
        PatientChartCard(clinicalCase = state.selectedCase)

        // 4. FHIR & Tokempic Inspector
        TokempicPromptCard(
            clinicalCase = state.selectedCase,
            isExpanded = state.showFhirRaw,
            selectedTab = state.fhirInspectorTab,
            selectedResourceType = state.selectedResourceType,
            matchedProtocol = state.matchedProtocol,
            allProtocols = state.allProtocols,
            selectedProtocolViewUnit = state.selectedProtocolViewUnit,
            onTabSelected = { viewModel.setFhirInspectorTab(it) },
            onResourceTypeSelected = { viewModel.setSelectedResourceType(it) },
            onProtocolSelected = { viewModel.selectProtocolForInspection(it) },
            onToggle = { viewModel.toggleFhirRaw() }
        )

        // 5. Offline Inference Action Buttons
        InferenceActionButtons(
            isInferring = state.isInferring,
            activeType = state.activeInferenceType,
            matchedProtocolId = state.matchedProtocol?.id?.removePrefix("KENYA-MOH-"),
            onRunBriefing = { viewModel.runInference(InferenceType.BRIEFING) },
            onRunTriage = { viewModel.runInference(InferenceType.TRIAGE_ADVICE) }
        )

        // 6. Streaming Inference Output & Telemetry
        if (state.generatedText.isNotEmpty() || state.isInferring) {
            InferenceResultCard(
                text = state.generatedText,
                isStreaming = state.isInferring,
                type = state.activeInferenceType,
                telemetry = state.telemetry,
                guardrailNote = state.selectedCase.guardrailNote,
                groundedProtocol = if (state.activeInferenceType == InferenceType.TRIAGE_ADVICE) state.matchedProtocol else null,
                onViewProtocolInRag = {
                    state.matchedProtocol?.let { proto ->
                        viewModel.openProtocolInRag(proto.id)
                    }
                }
            )
        }
    }
}

@Composable
fun HeaderSection() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Open Health Stack",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Surface(
                    color = Color(0xFF2E7D32),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "100% OFFLINE",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Kotlin Multiplatform FHIR + On-Device 2B SLM",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
            )
        }
    }
}

@Composable
fun CaseSelector(
    cases: List<ClinicalCase>,
    selectedCaseId: String,
    onSelect: (String) -> Unit
) {
    Column {
        Text(
            text = "Select Clinical Encounter Case:",
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            cases.forEach { c ->
                val isSelected = c.id == selectedCaseId
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onSelect(c.id) }
                        .border(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.LightGray,
                            shape = RoundedCornerShape(12.dp)
                        ),
                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                ) {
                    Column(
                        modifier = Modifier.padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = c.patientName.split(" ").first(),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = c.domain,
                            fontSize = 10.sp,
                            color = Color.Gray
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PatientChartCard(clinicalCase: ClinicalCase) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(text = clinicalCase.patientName, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text(text = "${clinicalCase.ageAndDob} • ${clinicalCase.village}", fontSize = 12.sp, color = Color.DarkGray)
                }
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = clinicalCase.gestation,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            HorizontalDivider(color = Color.LightGray.copy(alpha = 0.5f))

            // Conditions
            Text(text = "Active: ${clinicalCase.activeCondition}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            if (clinicalCase.resolvedHistory.isNotEmpty()) {
                Text(
                    text = "Past History: ${clinicalCase.resolvedHistory}",
                    fontSize = 12.sp,
                    color = Color(0xFF6B7280)
                )
            }

            // Vitals Badges
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(
                    color = if (clinicalCase.vitals.isBpHigher) Color(0xFFFFEBEE) else Color(0xFFE8F5E9),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(text = "Blood Pressure", fontSize = 10.sp, color = Color.Gray)
                        Text(
                            text = clinicalCase.vitals.bloodPressure,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = if (clinicalCase.vitals.isBpHigher) Color(0xFFC62828) else Color(0xFF2E7D32)
                        )
                    }
                }

                clinicalCase.vitals.labResult?.let { lab ->
                    Surface(
                        color = Color(0xFFFFF3E0),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1.5f)
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text(text = "Diagnostic POC", fontSize = 10.sp, color = Color.Gray)
                            Text(
                                text = lab,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = Color(0xFFE65100)
                            )
                        }
                    }
                }
            }

            // Urgent alert banner
            Surface(
                color = Color(0xFFFFEBEE),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "⚠️ ${clinicalCase.pendingTask}",
                    color = Color(0xFFC62828),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(8.dp)
                )
            }
        }
    }
}

@Composable
fun TokempicPromptCard(
    clinicalCase: ClinicalCase,
    isExpanded: Boolean,
    selectedTab: Int,
    selectedResourceType: String,
    matchedProtocol: ProtocolUnit? = null,
    allProtocols: List<ProtocolUnit> = emptyList(),
    selectedProtocolViewUnit: ProtocolUnit? = null,
    onTabSelected: (Int) -> Unit,
    onResourceTypeSelected: (String) -> Unit,
    onProtocolSelected: (String) -> Unit = {},
    onToggle: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggle() },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "FHIR & Tokempic Inspector",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "Raw FHIR Bundle (~1,150 tok) ➔ Tokempic (~320 tok)",
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isExpanded) "Hide ▲" else "Inspect ▼",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier.padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Tab Selector
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFF1F5F9))
                            .padding(3.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Surface(
                            onClick = { onTabSelected(0) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(6.dp),
                            color = if (selectedTab == 0) MaterialTheme.colorScheme.primary else Color.Transparent
                        ) {
                            Text(
                                text = "⚡ Tokempic",
                                fontSize = 11.sp,
                                fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal,
                                color = if (selectedTab == 0) Color.White else Color(0xFF475569),
                                modifier = Modifier.padding(vertical = 8.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                        Surface(
                            onClick = { onTabSelected(1) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(6.dp),
                            color = if (selectedTab == 1) MaterialTheme.colorScheme.primary else Color.Transparent
                        ) {
                            Text(
                                text = "🔥 Raw FHIR",
                                fontSize = 11.sp,
                                fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal,
                                color = if (selectedTab == 1) Color.White else Color(0xFF475569),
                                modifier = Modifier.padding(vertical = 8.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                        Surface(
                            onClick = { onTabSelected(2) },
                            modifier = Modifier.weight(1.2f),
                            shape = RoundedCornerShape(6.dp),
                            color = if (selectedTab == 2) Color(0xFF7E22CE) else Color.Transparent
                        ) {
                            Text(
                                text = "📚 Mini-RAG (20)",
                                fontSize = 11.sp,
                                fontWeight = if (selectedTab == 2) FontWeight.Bold else FontWeight.Normal,
                                color = if (selectedTab == 2) Color.White else Color(0xFF475569),
                                modifier = Modifier.padding(vertical = 8.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }

                    if (selectedTab == 0) {
                        // Tokempic Compressed Tabular View
                        Surface(
                            color = Color(0xFFF0FDF4),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFBBF7D0)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(text = "📉", fontSize = 12.sp)
                                Text(
                                    text = "68% Token Reduction (1,150 ➔ 320 tokens) optimized for on-device edge SLM context.",
                                    fontSize = 11.sp,
                                    color = Color(0xFF166534),
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        Surface(
                            color = Color(0xFF1E293B),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = clinicalCase.tokempicMarkdown,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = Color(0xFFE2E8F0),
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    } else if (selectedTab == 1) {
                        // Raw FHIR Resources
                        Surface(
                            color = Color(0xFFEFF6FF),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFBFDBFE)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(text = "📦", fontSize = 12.sp)
                                Text(
                                    text = "Authentic OHS FHIR R4 Bundle stored in local datastore before Tokempic table synthesis.",
                                    fontSize = 11.sp,
                                    color = Color(0xFF1E40AF),
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        // Filter Chips - Full Width Responsive Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            val types = listOf(
                                "ALL" to "All (${clinicalCase.fhirResources.size})",
                                "Patient" to "Patient",
                                "Condition" to "Cond",
                                "Observation" to "Obs",
                                "Task" to "Task"
                            )
                            types.forEach { (typeKey, label) ->
                                val isSelected = selectedResourceType == typeKey
                                Surface(
                                    onClick = { onResourceTypeSelected(typeKey) },
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSelected) Color(0xFF0F766E) else Color(0xFFE2E8F0),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = label,
                                        fontSize = 10.5.sp,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) Color.White else Color(0xFF334155),
                                        modifier = Modifier.padding(vertical = 7.dp)
                                    )
                                }
                            }
                        }

                        // Resource Content
                        if (selectedResourceType == "ALL") {
                            Surface(
                                color = Color(0xFF0F172A),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = clinicalCase.fhirBundleJson,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.5.sp,
                                    color = Color(0xFF38BDF8),
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        } else {
                            val filtered = clinicalCase.fhirResources.filter { it.resourceType == selectedResourceType }
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                filtered.forEach { res ->
                                    Card(
                                        shape = RoundedCornerShape(8.dp),
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(10.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Surface(
                                                    color = when (res.resourceType) {
                                                        "Patient" -> Color(0xFF0284C7)
                                                        "Condition" -> Color(0xFFDC2626)
                                                        "Observation" -> Color(0xFFD97706)
                                                        "Task" -> Color(0xFF7C3AED)
                                                        else -> Color.Gray
                                                    },
                                                    shape = RoundedCornerShape(4.dp)
                                                ) {
                                                    Text(
                                                        text = res.resourceType,
                                                        color = Color.White,
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                                Text(
                                                    text = res.resourceId,
                                                    color = Color(0xFF94A3B8),
                                                    fontSize = 10.sp,
                                                    fontFamily = FontFamily.Monospace
                                                )
                                            }
                                            Text(
                                                text = res.title,
                                                color = Color(0xFFE2E8F0),
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 11.sp,
                                                modifier = Modifier.padding(vertical = 4.dp)
                                            )
                                            HorizontalDivider(color = Color(0xFF334155))
                                            Text(
                                                text = res.jsonContent,
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 10.5.sp,
                                                color = Color(0xFF34D399),
                                                modifier = Modifier.padding(top = 6.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else if (selectedTab == 2) {
                        // MINI-RAG PROMPT & PROTOCOL INSPECTOR
                        val matched = selectedProtocolViewUnit ?: matchedProtocol
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Surface(
                                color = Color(0xFFF3E8FF),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFD8B4FE)),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(text = "🇰🇪", fontSize = 16.sp)
                                    Column {
                                        Text(
                                            text = "Kenya MOH eCHIS • Certified Offline Protocol Database",
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF581C87)
                                        )
                                        Text(
                                            text = "20 Clinical Decision Units (CDUs) pre-indexed in SQLite FTS5 (<1ms edge retrieval)",
                                            fontSize = 10.sp,
                                            color = Color(0xFF6B21A8)
                                        )
                                    }
                                }
                            }

                            // Protocol Selector Chips (Horizontal Scroll)
                            if (allProtocols.isNotEmpty()) {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        text = "SELECT PROTOCOL UNIT TO AUDIT:",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF64748B)
                                    )
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        allProtocols.forEach { proto ->
                                            val isCurrent = proto.id == matched?.id
                                            val shortId = proto.id.removePrefix("KENYA-MOH-")
                                            Surface(
                                                onClick = { onProtocolSelected(proto.id) },
                                                shape = RoundedCornerShape(12.dp),
                                                color = if (isCurrent) Color(0xFF7E22CE) else Color(0xFFF1F5F9),
                                                modifier = Modifier.padding(vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = "$shortId ${if (isCurrent) "✓" else ""}",
                                                    fontSize = 10.5.sp,
                                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                                    color = if (isCurrent) Color.White else Color(0xFF334155),
                                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            if (matched != null) {
                                Card(
                                    shape = RoundedCornerShape(10.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1B4B)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "${matched.id}: ${matched.topic}",
                                                color = Color(0xFFC084FC),
                                                fontSize = 12.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Surface(
                                                color = Color(0xFF15803D),
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    text = "✓ CERTIFIED",
                                                    color = Color.White,
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }

                                        Text(
                                            text = "${matched.sourceManual} (${matched.sourceCode})",
                                            color = Color(0xFF94A3B8),
                                            fontSize = 10.5.sp
                                        )

                                        if (matched.verbatimExcerpt.isNotEmpty()) {
                                            Surface(
                                                color = Color(0xFF2E1065),
                                                shape = RoundedCornerShape(6.dp),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Text(
                                                    text = "\"${matched.verbatimExcerpt}\"",
                                                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                                    color = Color(0xFFE9D5FF),
                                                    fontSize = 10.5.sp,
                                                    modifier = Modifier.padding(8.dp)
                                                )
                                            }
                                        }

                                        // Strict Negative Boundaries Box
                                        if (matched.negativeBoundaries.isNotEmpty()) {
                                            Surface(
                                                color = Color(0xFF450A0A),
                                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEF4444)),
                                                shape = RoundedCornerShape(6.dp),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Column(modifier = Modifier.padding(8.dp)) {
                                                    Text(
                                                        text = "🛑 EXPLICIT NEGATIVE BOUNDARIES (SAFETY CLAMPED):",
                                                        color = Color(0xFFFCA5A5),
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                    matched.negativeBoundaries.forEach { neg ->
                                                        Text(
                                                            text = "• $neg",
                                                            color = Color(0xFFFECACA),
                                                            fontSize = 10.sp,
                                                            modifier = Modifier.padding(top = 2.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        // Prompt Injection Chunk
                                        Text(
                                            text = "On-Device SLM Prompt Injection Chunk:",
                                            color = Color(0xFFA5B4FC),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Surface(
                                            color = Color(0xFF0F172A),
                                            shape = RoundedCornerShape(6.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                text = matched.promptInjectionChunk,
                                                fontFamily = FontFamily.Monospace,
                                                color = Color(0xFFE2E8F0),
                                                fontSize = 10.sp,
                                                modifier = Modifier.padding(8.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun InferenceActionButtons(
    isInferring: Boolean,
    activeType: InferenceType,
    matchedProtocolId: String? = null,
    onRunBriefing: () -> Unit,
    onRunTriage: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = onRunBriefing,
            enabled = !isInferring,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            if (isInferring && activeType == InferenceType.BRIEFING) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Streaming Pre-Visit Briefing...")
            } else {
                Text("⚡ Generate Pre-Visit Briefing (3-Bullet SBAR)")
            }
        }

        OutlinedButton(
            onClick = onRunTriage,
            enabled = !isInferring,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            if (isInferring && activeType == InferenceType.TRIAGE_ADVICE) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Evaluating Grounded Protocol (${matchedProtocolId ?: "MOH"})...")
            } else {
                Text("🩺 Run Protocol Triage (Grounded in ${matchedProtocolId ?: "MOH eCHIS"})")
            }
        }
    }
}

@Composable
fun InferenceResultCard(
    text: String,
    isStreaming: Boolean,
    type: InferenceType,
    telemetry: com.example.ohsedgedemo.ui.main.InferenceTelemetry?,
    guardrailNote: String,
    groundedProtocol: com.example.ohsedgedemo.data.ProtocolUnit? = null,
    onViewProtocolInRag: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (type == InferenceType.BRIEFING) "📋 Pre-Visit Briefing" else "🩺 Protocol Triage Advice",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = Color(0xFF0F172A)
                )
                if (isStreaming) {
                    Text(
                        text = if (groundedProtocol != null) "● Grounded Streaming" else "● Streaming",
                        color = if (groundedProtocol != null) Color(0xFF16A34A) else Color(0xFF2563EB),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Dedicated Grounding Card for RAG Protocol Guidance
            if (type == InferenceType.TRIAGE_ADVICE && groundedProtocol != null) {
                Surface(
                    color = Color(0xFFF0FDF4),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF86EFAC)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text("🌿", fontSize = 14.sp)
                                Text(
                                    text = "GROUNDED IN PROTOCOL ENTRY",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF15803D)
                                )
                            }
                            Surface(
                                color = Color(0xFFDCFCE7),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = "✓ Offline SQLite FTS5 RAG",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF166534),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Text(
                            text = "${groundedProtocol.id}: ${groundedProtocol.topic}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A)
                        )

                        Text(
                            text = "${groundedProtocol.sourceManual} • ${groundedProtocol.sourceCode}",
                            fontSize = 11.sp,
                            color = Color(0xFF475569)
                        )

                        // Verbatim quote excerpt from the offline knowledge base
                        Surface(
                            color = Color.White,
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFBBF7D0)),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "\"${groundedProtocol.verbatimExcerpt.take(150)}${if (groundedProtocol.verbatimExcerpt.length > 150) "..." else ""}\"",
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                fontSize = 11.sp,
                                color = Color(0xFF1E293B),
                                modifier = Modifier.padding(8.dp)
                            )
                        }

                        if (groundedProtocol.negativeBoundaries.isNotEmpty()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text("🛑", fontSize = 10.sp)
                                Text(
                                    text = "Enforced: ${groundedProtocol.negativeBoundaries.first()}",
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFFB91C1C)
                                )
                            }
                        }

                        onViewProtocolInRag?.let { onInspect ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { onInspect() }
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Audit full protocol entry in Mini-RAG tab ↗",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF15803D)
                                )
                            }
                        }
                    }
                }
            }

            ClinicalMarkdownView(
                text = text,
                modifier = Modifier.fillMaxWidth()
            )

            // Telemetry HUD
            telemetry?.let {
                HorizontalDivider(color = Color.LightGray.copy(alpha = 0.5f))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "⚡ ${it.tokensPerSecond} tok/s", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F766E))
                    Text(text = "⏱️ TTFT: ${it.timeToFirstTokenMs}ms", fontSize = 11.sp, color = Color.Gray)
                    Text(text = "💾 ${it.memoryUsageMb} MB RAM", fontSize = 11.sp, color = Color.Gray)
                }
                Text(
                    text = "Delegate: ${it.hardwareDelegate}",
                    fontSize = 10.sp,
                    color = Color.Gray
                )
            }

            // Safety Guardrail validation banner
            Surface(
                color = Color(0xFFEFF6FF),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "🛡️ $guardrailNote",
                    color = Color(0xFF1D4ED8),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(8.dp)
                )
            }
        }
    }
}
