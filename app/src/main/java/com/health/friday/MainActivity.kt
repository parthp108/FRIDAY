
package com.health.friday

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.health.friday.assistant.AssistantScreen
import com.health.friday.ai.AiOrchestrator
import com.health.friday.ai.AiToolRegistry
import com.health.friday.ai.LocalAiClient
import com.health.friday.data.local.FridayDatabase
import com.health.friday.data.local.WaterDao
import com.health.friday.nutrition.LocalNutritionProvider
import com.health.friday.nutrition.NutritionRepository
import com.health.friday.nutrition.NutritionScreen
import com.health.friday.ui.theme.FRIDAYTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        val database =
            FridayDatabase.getDatabase(this)

        val nutritionProvider =
            LocalNutritionProvider()

        val nutritionRepository =
            NutritionRepository(
                mealDao = database.mealDao(),
                nutritionProvider = nutritionProvider
            )

        val waterDao =
            database.waterDao()

        val aiClient =
            LocalAiClient()

        val aiToolRegistry =
            AiToolRegistry()

        val aiOrchestrator =
            AiOrchestrator(
                aiClient = aiClient,
                toolRegistry = aiToolRegistry
            )

        setContent {

            FRIDAYTheme {

                FridayApp(
                    nutritionRepository = nutritionRepository,
                    waterDao = waterDao,
                    aiOrchestrator = aiOrchestrator
                )
            }
        }
    }
}

@Composable
fun FridayApp(
    nutritionRepository: NutritionRepository,
    waterDao: WaterDao,
    aiOrchestrator: AiOrchestrator
) {

    var selectedScreen by remember {
        mutableStateOf(0)
    }

    val screens =
        listOf(
            "Home",
            "Health",
            "Nutrition",
            "FRIDAY",
            "Settings"
        )

    Scaffold(

        bottomBar = {

            NavigationBar {

                screens.forEachIndexed { index, screen ->

                    NavigationBarItem(

                        selected =
                            selectedScreen == index,

                        onClick = {
                            selectedScreen = index
                        },

                        icon = {

                            Text(
                                text =
                                    when (index) {
                                        0 -> "⌂"
                                        1 -> "♥"
                                        2 -> "🍽"
                                        3 -> "●"
                                        else -> "⚙"
                                    }
                            )
                        },

                        label = {
                            Text(screen)
                        }
                    )
                }
            }
        }

    ) { innerPadding ->

        when (selectedScreen) {

            0 -> FridayHomeScreen(
                modifier =
                    Modifier.padding(innerPadding)
            )

            1 -> PlaceholderScreen(
                title = "Health",
                modifier =
                    Modifier.padding(innerPadding)
            )

            2 -> NutritionScreen(
                repository =
                    nutritionRepository,
                waterDao =
                    waterDao,
                modifier =
                    Modifier.padding(innerPadding)
            )

            3 -> AssistantScreen(
                orchestrator =
                    aiOrchestrator,
                modifier =
                    Modifier.padding(innerPadding)
            )

            4 -> PlaceholderScreen(
                title = "Settings",
                modifier =
                    Modifier.padding(innerPadding)
            )
        }
    }
}

@Composable
fun FridayHomeScreen(
    modifier: Modifier = Modifier
) {

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(20.dp),

        verticalArrangement =
            Arrangement.Top
    ) {

        Text(
            text = "FRIDAY",
            style =
                MaterialTheme.typography.headlineMedium
        )

        Text(
            text = "Your personal operating system",
            style =
                MaterialTheme.typography.bodyLarge,

            modifier =
                Modifier.padding(top = 6.dp)
        )

        Text(
            text = "Health, life and device intelligence.",
            style =
                MaterialTheme.typography.bodyMedium,

            modifier =
                Modifier.padding(top = 4.dp)
        )

        Text(
            text = "Sleep      7h 42m",
            modifier =
                Modifier.padding(top = 30.dp)
        )

        Text(
            text = "Heart      72 bpm",
            modifier =
                Modifier.padding(top = 16.dp)
        )

        Text(
            text = "Steps      3,241",
            modifier =
                Modifier.padding(top = 16.dp)
        )

        Text(
            text = "Water      1.2 L",
            modifier =
                Modifier.padding(top = 16.dp)
        )
    }
}

@Composable
fun PlaceholderScreen(
    title: String,
    modifier: Modifier = Modifier
) {

    Column(
        modifier =
            modifier.fillMaxSize(),

        horizontalAlignment =
            Alignment.CenterHorizontally,

        verticalArrangement =
            Arrangement.Center
    ) {

        Text(
            text = title,
            style =
                MaterialTheme.typography.headlineMedium
        )

        Text(
            text = "Coming next...",
            modifier =
                Modifier.padding(top = 8.dp)
        )
    }
}
