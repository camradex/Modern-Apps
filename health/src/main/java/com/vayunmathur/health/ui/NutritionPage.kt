package com.vayunmathur.health.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vayunmathur.health.R
import com.vayunmathur.health.Route
import com.vayunmathur.health.data.RecordType
import com.vayunmathur.health.ui.components.GroupedSection
import com.vayunmathur.health.ui.components.MetricRing
import com.vayunmathur.health.util.HealthViewModel
import com.vayunmathur.library.util.NavBackStack
import com.vayunmathur.library.util.round
import com.vayunmathur.library.ui.*
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlinx.datetime.*

data class NutrientDV(
    val name: String,
    val type: RecordType,
    val dailyValue: Double,
    val unit: String,
    val sumFunction: (RecordType, Instant, Instant) -> kotlinx.coroutines.flow.Flow<Double>
)

/** Shared nutrient catalog used by both NutritionPage and NutritionDetailsPage. */
internal fun nutrientCatalog(viewModel: HealthViewModel): List<NutrientDV> = listOf(
    NutrientDV("Protein", RecordType.Nutrition, 50.0, "g") { t, s, e -> viewModel.sumProteinInRange(t, s, e) },
    NutrientDV("Carbohydrates", RecordType.Nutrition, 275.0, "g") { t, s, e -> viewModel.sumCarbsInRange(t, s, e) },
    NutrientDV("Fat", RecordType.Nutrition, 78.0, "g") { t, s, e -> viewModel.sumFatInRange(t, s, e) },
    NutrientDV("Fiber", RecordType.Nutrition, 28.0, "g") { t, s, e -> viewModel.sumFiberInRange(t, s, e) },
    NutrientDV("Sugar", RecordType.Nutrition, 50.0, "g") { t, s, e -> viewModel.sumSugarInRange(t, s, e) },
    NutrientDV("Sodium", RecordType.Nutrition, 2300.0, "mg") { t, s, e -> viewModel.sumSodiumInRange(t, s, e) },
    NutrientDV("Cholesterol", RecordType.Nutrition, 300.0, "mg") { t, s, e -> viewModel.sumCholesterolInRange(t, s, e) },
    NutrientDV("Saturated Fat", RecordType.Nutrition, 20.0, "g") { t, s, e -> viewModel.sumSaturatedFatInRange(t, s, e) },
    NutrientDV("Trans Fat", RecordType.Nutrition, 2.0, "g") { t, s, e -> viewModel.sumTransFatInRange(t, s, e) },
    NutrientDV("Vitamin A", RecordType.Nutrition, 900.0, "µg") { t, s, e -> viewModel.sumVitaminAInRange(t, s, e) },
    NutrientDV("Vitamin C", RecordType.Nutrition, 90.0, "mg") { t, s, e -> viewModel.sumVitaminCInRange(t, s, e) },
    NutrientDV("Vitamin D", RecordType.Nutrition, 20.0, "µg") { t, s, e -> viewModel.sumVitaminDInRange(t, s, e) },
    NutrientDV("Vitamin E", RecordType.Nutrition, 15.0, "mg") { t, s, e -> viewModel.sumVitaminEInRange(t, s, e) },
    NutrientDV("Vitamin K", RecordType.Nutrition, 120.0, "µg") { t, s, e -> viewModel.sumVitaminKInRange(t, s, e) },
    NutrientDV("Vitamin B6", RecordType.Nutrition, 1.7, "mg") { t, s, e -> viewModel.sumVitaminB6InRange(t, s, e) },
    NutrientDV("Vitamin B12", RecordType.Nutrition, 2.4, "µg") { t, s, e -> viewModel.sumVitaminB12InRange(t, s, e) },
    NutrientDV("Thiamin", RecordType.Nutrition, 1.2, "mg") { t, s, e -> viewModel.sumThiaminInRange(t, s, e) },
    NutrientDV("Riboflavin", RecordType.Nutrition, 1.3, "mg") { t, s, e -> viewModel.sumRiboflavinInRange(t, s, e) },
    NutrientDV("Niacin", RecordType.Nutrition, 16.0, "mg") { t, s, e -> viewModel.sumNiacinInRange(t, s, e) },
    NutrientDV("Folate", RecordType.Nutrition, 400.0, "µg") { t, s, e -> viewModel.sumFolateInRange(t, s, e) },
    NutrientDV("Biotin", RecordType.Nutrition, 30.0, "µg") { t, s, e -> viewModel.sumBiotinInRange(t, s, e) },
    NutrientDV("Pantothenic Acid", RecordType.Nutrition, 5.0, "mg") { t, s, e -> viewModel.sumPantothenicAcidInRange(t, s, e) },
    NutrientDV("Calcium", RecordType.Nutrition, 1300.0, "mg") { t, s, e -> viewModel.sumCalciumInRange(t, s, e) },
    NutrientDV("Iron", RecordType.Nutrition, 18.0, "mg") { t, s, e -> viewModel.sumIronInRange(t, s, e) },
    NutrientDV("Magnesium", RecordType.Nutrition, 420.0, "mg") { t, s, e -> viewModel.sumMagnesiumInRange(t, s, e) },
    NutrientDV("Phosphorus", RecordType.Nutrition, 1250.0, "mg") { t, s, e -> viewModel.sumPhosphorusInRange(t, s, e) },
    NutrientDV("Iodine", RecordType.Nutrition, 150.0, "µg") { t, s, e -> viewModel.sumIodineInRange(t, s, e) },
    NutrientDV("Zinc", RecordType.Nutrition, 11.0, "mg") { t, s, e -> viewModel.sumZincInRange(t, s, e) },
    NutrientDV("Selenium", RecordType.Nutrition, 55.0, "µg") { t, s, e -> viewModel.sumSeleniumInRange(t, s, e) },
    NutrientDV("Copper", RecordType.Nutrition, 0.9, "mg") { t, s, e -> viewModel.sumCopperInRange(t, s, e) },
    NutrientDV("Manganese", RecordType.Nutrition, 2.3, "mg") { t, s, e -> viewModel.sumManganeseInRange(t, s, e) },
    NutrientDV("Chromium", RecordType.Nutrition, 35.0, "µg") { t, s, e -> viewModel.sumChromiumInRange(t, s, e) },
    NutrientDV("Molybdenum", RecordType.Nutrition, 45.0, "µg") { t, s, e -> viewModel.sumMolybdenumInRange(t, s, e) },
    NutrientDV("Chloride", RecordType.Nutrition, 2300.0, "mg") { t, s, e -> viewModel.sumChlorideInRange(t, s, e) },
    NutrientDV("Potassium", RecordType.Nutrition, 4700.0, "mg") { t, s, e -> viewModel.sumPotassiumInRange(t, s, e) },
    NutrientDV("Caffeine", RecordType.Nutrition, 400.0, "mg") { t, s, e -> viewModel.sumCaffeineInRange(t, s, e) },
    NutrientDV("Hydration", RecordType.Hydration, 3.0, "L") { t, s, e -> viewModel.sumInRange(t, s, e) }
)

/**
 * Slim nutrition home — answers "how am I doing on nutrition today?" at a glance.
 * Full meal log + nutrient breakdown live in [NutritionDetailsPage].
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun NutritionPage(backStack: NavBackStack<Route>, viewModel: HealthViewModel) {
    val tz = TimeZone.currentSystemDefault()
    val today = Clock.System.todayIn(tz)
    val dayStart = today.atStartOfDayIn(tz)
    val dayEnd = dayStart.plus(24.hours)

    val totalCalories by remember(dayStart, dayEnd) {
        viewModel.sumInRange(RecordType.Nutrition, dayStart, dayEnd)
    }.collectAsState(0.0)
    val totalProtein by remember(dayStart, dayEnd) {
        viewModel.sumProteinInRange(RecordType.Nutrition, dayStart, dayEnd)
    }.collectAsState(0.0)
    val totalCarbs by remember(dayStart, dayEnd) {
        viewModel.sumCarbsInRange(RecordType.Nutrition, dayStart, dayEnd)
    }.collectAsState(0.0)
    val totalFat by remember(dayStart, dayEnd) {
        viewModel.sumFatInRange(RecordType.Nutrition, dayStart, dayEnd)
    }.collectAsState(0.0)
    val loggedMeals by remember(dayStart, dayEnd) {
        viewModel.getAllRecordsInRange(RecordType.Nutrition, dayStart, dayEnd)
    }.collectAsState(emptyList())

    var fabExpanded by remember { mutableStateOf(false) }
    var showHydrationDialog by remember { mutableStateOf(false) }
    var showMealDialog by remember { mutableStateOf(false) }

    if (showHydrationDialog) {
        LogHydrationDialog(viewModel = viewModel, initialTime = null, onDismiss = { showHydrationDialog = false })
    }
    if (showMealDialog) {
        LogMealDialog(viewModel = viewModel, initialTime = null, onDismiss = { showMealDialog = false })
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.label_nutrition_details)) })
        },
        floatingActionButton = {
            FloatingActionButtonMenu(
                expanded = fabExpanded,
                button = {
                    ToggleFloatingActionButton(fabExpanded, { fabExpanded = it }) {
                        if (!fabExpanded) IconAdd() else IconClose()
                    }
                }
            ) {
                FloatingActionButtonMenuItem(
                    onClick = { fabExpanded = false; showHydrationDialog = true },
                    text = { Text("Log Hydration") },
                    icon = { IconFire() }
                )
                FloatingActionButtonMenuItem(
                    onClick = { fabExpanded = false; showMealDialog = true },
                    text = { Text("Log Meal") },
                    icon = { IconFire() }
                )
                FloatingActionButtonMenuItem(
                    onClick = { fabExpanded = false; backStack.add(Route.RecipeManagement) },
                    text = { Text("Recipes") },
                    icon = { IconAdd() }
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Calorie ring
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    val caloriesGoal = 2000.0
                    val calorieProgress = (totalCalories / caloriesGoal).toFloat().coerceIn(0f, 1f)
                    MetricRing(
                        progress = calorieProgress,
                        label = "kcal",
                        value = totalCalories.round(0).toInt().toString(),
                        modifier = Modifier.size(140.dp),
                        color = HealthColors.Nutrition,
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Calories",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "${totalCalories.round(0).toInt()} / ${caloriesGoal.toInt()} kcal",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }

            // Macros as 3 compact rings
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                CompactMacroRing("Protein", totalProtein, 50.0, "g", proteinColor)
                CompactMacroRing("Carbs", totalCarbs, 275.0, "g", carbsColor)
                CompactMacroRing("Fat", totalFat, 78.0, "g", fatColor)
            }

            // Meals link + breakdown CTA
            GroupedSection(accentColor = HealthColors.Nutrition) {
                val totalCal = loggedMeals.sumOf { it.nutritionData?.calories ?: 0.0 }
                ListItem(
                    headlineContent = {
                        Text(
                            if (loggedMeals.isEmpty()) "No meals logged today"
                            else "${loggedMeals.size} meals · ${totalCal.round(0).toInt()} cal"
                        )
                    },
                    supportingContent = { Text("View full breakdown") },
                    trailingContent = {
                        Icon(
                            painter = painterResource(R.drawable.outline_arrow_forward_24),
                            contentDescription = null,
                            tint = HealthColors.Nutrition,
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable { backStack.add(Route.NutritionFullBreakdown) },
                )
            }
        }
    }
}

@Composable
private fun CompactMacroRing(
    label: String,
    value: Double,
    goal: Double,
    unit: String,
    color: Color,
) {
    val progress = (value / goal).toFloat().coerceIn(0f, 1f)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        MetricRing(
            progress = progress,
            label = "",
            value = "${value.round(0).toInt()}",
            modifier = Modifier.size(72.dp),
            color = color,
            strokeWidth = 6.dp,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = color.copy(alpha = 0.95f),
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "${value.round(0).toInt()}/${goal.toInt()}$unit",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Single nutrient row used by NutritionDetailsPage. */
@Composable
internal fun NutrientProgressRow(nutrient: NutrientDV, start: Instant, end: Instant) {
    val currentAmount by remember(nutrient, start, end) {
        nutrient.sumFunction(nutrient.type, start, end)
    }.collectAsState(0.0)
    val progress = (currentAmount / nutrient.dailyValue).toFloat().coerceIn(0f, 1f)
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = nutrient.name,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "${currentAmount.round(1)} / ${nutrient.dailyValue.round(0).toInt()}${nutrient.unit}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().height(4.dp),
            color = HealthColors.Nutrition,
            trackColor = HealthColors.Nutrition.copy(alpha = 0.18f),
            strokeCap = StrokeCap.Round,
        )
    }
}
