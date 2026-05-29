package com.vayunmathur.health.ui

import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.feature.ExperimentalPersonalHealthRecordApi
import com.google.fhir.model.r4b.Immunization
import com.vayunmathur.health.R
import com.vayunmathur.health.Route
import com.vayunmathur.health.ui.components.GroupedSection
import com.vayunmathur.health.ui.components.GroupedSectionDivider
import com.vayunmathur.health.ui.components.HealthRow
import com.vayunmathur.health.util.HealthViewModel
import com.vayunmathur.library.ui.BackupButtons
import com.vayunmathur.library.ui.IconNavigation
import com.vayunmathur.library.ui.IconUpload
import com.vayunmathur.library.util.NavBackStack
import com.vayunmathur.library.util.SecureResultReceiver
import androidx.core.net.toUri

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPersonalHealthRecordApi::class)
@Composable
fun ImmunizationsPage(backStack: NavBackStack<Route>, viewModel: HealthViewModel) {
    val context = LocalContext.current
    val immunizations by viewModel.immunizations.collectAsState()
    var isProcessing by remember { mutableStateOf(false) }
    var showInstallDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.refreshImmunizations()
    }

    if (showInstallDialog) {
        AlertDialog(
            onDismissRequest = { showInstallDialog = false },
            title = { Text(stringResource(R.string.open_assistant_required)) },
            text = { Text(stringResource(R.string.open_assistant_rationale)) },
            confirmButton = {
                TextButton(onClick = {
                    val intent = Intent(Intent.ACTION_VIEW,
                        "https://github.com/vayun-mathur/Modern-Apps".toUri())
                    context.startActivity(intent)
                    showInstallDialog = false
                }) {
                    Text(stringResource(R.string.view_on_github))
                }
            },
            dismissButton = {
                TextButton(onClick = { showInstallDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    val immunizationSchema = """
        {
          "type": "object",
          "description": "FHIR Immunization resource schema",
          "properties": {
            "resourceType": { "const": "Immunization" },
            "status": { "enum": ["completed", "entered-in-error", "not-done"] },
            "vaccineCode": {
              "type": "object",
              "properties": {
                "text": { "type": "string" }
              },
              "required": ["text"]
            },
            "patient": {
              "type": "object",
              "properties": {
                "display": { "type": "string" }
              },
              "required": ["display"]
            },
            "occurrenceDateTime": { 
              "type": "string",
              "pattern": "^([0-9]([0-9]([0-9][1-9]|[1-9]0)|[1-9]00)|[1-9]000)(-(0[1-9]|1[0-2])(-(0[1-9]|[1-2][0-9]|3[0-1])(T([01][0-9]|2[0-3]):[0-5][0-9]:([0-5][0-9]|60)(\\.[0-9]+)?(Z|(\\+|-)((0[0-9]|1[0-3]):[0-5][0-9]|14:00)))?)?)?$",
              "description": "ISO 8601 date-time string (e.g., 2023-07-13). MUST be in this format."
            },
            "lotNumber": { "type": "string" },
            "expirationDate": { 
              "type": "string",
              "pattern": "^([0-9]([0-9]([0-9][1-9]|[1-9]0)|[1-9]00)|[1-9]000)(-(0[1-9]|1[0-2])(-(0[1-9]|[1-2][0-9]|3[0-1]))?)?$",
              "description": "ISO 8601 date string (e.g., 2024-12-31). MUST be in this format."
            }
          },
          "required": ["resourceType", "status", "vaccineCode", "patient"]
        }
    """.trimIndent()

    val resultReceiver = remember {
        SecureResultReceiver(null) { resultCode, resultData ->
            isProcessing = false
            if (resultCode == 0) {
                val jsonResult = resultData?.getString("json_result")
                if (jsonResult != null) {
                    viewModel.writeImmunization(jsonResult)
                }
            }
        }
    }

    val pdfLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            isProcessing = true
            viewModel.extractMedicalDataFromPdf(
                uri = uri,
                userText = "Extract immunization details from these images.",
                schema = immunizationSchema,
                receiver = resultReceiver,
                onFailedToStart = { isProcessing = false }
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.label_immunizations)) },
                navigationIcon = {
                    IconNavigation(backStack)
                },
                actions = {
                    BackupButtons()
                }
            )
        },
        floatingActionButton = {
            if (immunizations.isNotEmpty()) {
                FloatingActionButton(onClick = {
                    if (isOpenAssistantInstalled(context)) {
                        pdfLauncher.launch("application/pdf")
                    } else {
                        showInstallDialog = true
                    }
                }) {
                    IconUpload()
                }
            }
        }
    ) { paddingValues ->
        Column(Modifier.padding(paddingValues)) {
            if (isProcessing) {
                Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp)) {
                    Row(
                        Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        Text(stringResource(R.string.msg_processing_document))
                    }
                }
            }

            if (immunizations.isEmpty() && !isProcessing) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Button(onClick = {
                        if (isOpenAssistantInstalled(context)) {
                            pdfLauncher.launch("application/pdf")
                        } else {
                            showInstallDialog = true
                        }
                    }) {
                        IconUpload()
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.msg_upload_first_immunization))
                    }
                }
            } else {
                GroupedSection(
                    title = stringResource(R.string.label_immunizations),
                    accentColor = HealthColors.Vitals,
                ) {
                    immunizations.forEachIndexed { idx, imm ->
                        if (idx > 0) GroupedSectionDivider()
                        ImmunizationRow(imm)
                    }
                }
            }
        }
    }
}

@Composable
fun ImmunizationRow(immunization: Immunization) {
    val name = immunization.vaccineCode.text?.value ?: stringResource(R.string.unknown)
    val status = immunization.status.value?.getDisplay() ?: stringResource(R.string.unknown)
    val occurrenceDisplay = when (val occ = immunization.occurrence) {
        is Immunization.Occurrence.DateTime -> occ.value.value?.toString()
        is Immunization.Occurrence.String -> occ.value.value
    }
    val lot = immunization.lotNumber?.value
    val supporting = buildList {
        add(stringResource(R.string.status_format, status))
        occurrenceDisplay?.let { add(stringResource(R.string.date_format_label, it)) }
        lot?.let { add(stringResource(R.string.lot_format, it)) }
    }.joinToString(" • ")
    HealthRow(
        headline = name,
        supporting = supporting,
        leadingIconRes = R.drawable.baseline_favorite_24,
        leadingTint = HealthColors.Vitals,
    )
}

internal fun isOpenAssistantInstalled(context: Context): Boolean {
    return try {
        context.packageManager.getPackageInfo("com.vayunmathur.openassistant", 0)
        true
    } catch (e: Exception) {
        false
    }
}
