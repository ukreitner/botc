package com.clocktower.engine

import com.clocktower.engine.rules.SaltAndLantern
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** The complete embedded dataset: characters, jinxes and wake orders. */
class GameData(
    val characters: List<Character>,
    val jinxes: List<Jinx>,
    val firstNightOrder: List<String>,
    val otherNightOrder: List<String>,
    private val bundledScripts: List<Script> = emptyList(),
) {
    private val byId: Map<String, Character> = characters.associateBy { it.id }

    /** Night order lists live on [NightPlan] now; WP2 deleted `NightOrder`. */

    fun character(id: String): Character? = byId[Character.normalizeId(id)]

    fun charactersOf(edition: String): List<Character> =
        characters.filter { it.edition == edition }

    /** Jinxes where both characters appear in [ids]. */
    fun activeJinxes(ids: Collection<String>, script: Script? = null): List<Jinx> {
        val set = ids.map { Character.normalizeId(it) }.toSet()
        return (jinxes + script?.jinxes.orEmpty()).distinct().filter { it.id1 in set && it.id2 in set }
    }

    /** Bundled official editions and authored homebrew scripts. */
    fun builtInScripts(): List<Script> = listOf(
        builtIn("tb", "Trouble Brewing"),
        builtIn("bmr", "Bad Moon Rising"),
        builtIn("sv", "Sects & Violets"),
    ) + bundledScripts

    fun nightOrder(script: Script, first: Boolean): List<String> =
        (if (first) script.firstNightOrder else script.otherNightOrder)
            .ifEmpty { if (first) firstNightOrder else otherNightOrder }

    private fun builtIn(edition: String, name: String): Script = Script(
        id = edition,
        name = name,
        author = "The Pandemonium Institute",
        characterIds = characters
            .filter { it.edition == edition && it.team.isTownResident }
            .map { it.id },
        isBuiltIn = true,
    )

    /**
     * Resolves a script's characters, including custom ones. Unknown ids are
     * skipped (the UI reports them separately via [unknownIds]).
     */
    fun resolve(script: Script): List<Character> {
        val custom = script.customCharacters.associateBy { it.id }
        return script.characterIds.mapNotNull { id -> custom[id] ?: character(id) }
    }

    fun unknownIds(script: Script): List<String> {
        val custom = script.customCharacters.associateBy { it.id }
        return script.characterIds.filter { custom[it] == null && character(it) == null }
    }

    /** Travellers legal for a script: its own plus every traveller of its editions. */
    fun travellersFor(script: Script): List<Character> {
        val editions = resolve(script).map { it.edition }.toSet()
        val fromScript = resolve(script).filter { it.team == Team.TRAVELLER }
        val fromEditions = characters.filter { it.team == Team.TRAVELLER && it.edition in editions }
        return (fromScript + fromEditions + charactersOf("tb").filter { it.team == Team.TRAVELLER })
            .distinctBy { it.id }
    }

    val allFabled: List<Character> get() = characters.filter { it.team == Team.FABLED }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        /** Loads the dataset bundled on the classpath. */
        fun loadDefault(): GameData {
            val characters = json.decodeFromString<List<Character>>(BotcResources.read("/botc/data/characters.json"))
            val salt = SaltAndLantern.load()
            val extras = json.decodeFromString<NightAndJinxes>(BotcResources.read("/botc/data/night_and_jinxes.json"))
            return GameData(
                characters = characters + salt.customCharacters,
                jinxes = extras.jinxes,
                bundledScripts = listOf(salt),
                firstNightOrder = extras.firstNight.map {
                    if (it in NightMarkers.all) it else Character.normalizeId(it)
                },
                otherNightOrder = extras.otherNight.map {
                    if (it in NightMarkers.all) it else Character.normalizeId(it)
                },
            )
        }

    }
}

@Serializable
data class NightAndJinxes(
    val jinxes: List<Jinx>,
    val firstNight: List<String>,
    val otherNight: List<String>,
)
