package com.clocktower.engine.rules

import com.clocktower.engine.*

/** The supplied v1.1 script. The written night sheet takes precedence over JSON indices. */
object SaltAndLantern {
    const val ID = "salt-and-lantern"
    // The official roster already owns ferryman (a Fabled). Keep both identities and art.
    const val FERRYMAN_ID = "saltlanternferryman"
    private fun bundledId(id: String): String = if (id == "ferryman") FERRYMAN_ID else id

    fun load(): Script {
        val source = ScriptParser.parse(BotcResources.read("/botc/data/salt-and-lantern.json"))
        return source.copy(
            id = ID,
            isBuiltIn = true,
            resurrectionRestoresAbilities = false,
            characterIds = source.characterIds.map(::bundledId),
            customCharacters = source.customCharacters.map {
                it.copy(id = bundledId(it.id), spentLabel = when (it.id) {
                    "navigator" -> "No ability"
                    "harbourmaster" -> "Closed"
                    else -> ""
                })
            },
            // These three interactions are present in the written v1.1 rulings,
            // but absent from its companion JSON. Preserve both source documents.
            jinxes = (source.jinxes + listOf(
                Jinx("devilsadvocate", "albatross", "The Albatross triggers on being executed, not on dying. An Albatross protected by the Devil's Advocate who is executed survives, and their nominator still dies tonight."),
                Jinx("devilsadvocate", "ferryman", "A Ferryman who is executed but survives has not died; no crossing."),
                Jinx("devilsadvocate", "kraken", "A Minion who is executed but survives has not died; no thrash."),
            )).map { it.copy(id1 = bundledId(it.id1), id2 = bundledId(it.id2)) },
            firstNightOrder = listOf(
                NightMarkers.DUSK, NightMarkers.MINION_INFO, NightMarkers.DEMON_INFO,
                "poisoner", "wrecker", "devilsadvocate", "pukka", "investigator", "chef",
                "empath", "cartographer", "fortuneteller", "smuggler", NightMarkers.DAWN,
            ),
            otherNightOrder = listOf(
                NightMarkers.DUSK, "poisoner", "wrecker", "devilsadvocate", "lighthousekeeper",
                "imp", "pukka", "siren", "kraken", FERRYMAN_ID, "ravenkeeper", "pearldiver",
                "empath", "fortuneteller", "navigator", "smuggler", NightMarkers.DAWN,
            ),
            manualNightInstructions = source.customCharacters.associate { character ->
                bundledId(character.id) to manualInstructions.getValue(character.id)
            } + manualInstructions.filterKeys { it in setOf("chef", "empath", "fortuneteller", "ravenkeeper", "imp", "pukka") },
            storytellerNotes = notes,
            duskReminder = dusk,
            dawnReminder = dawn,
            dayReminder = day,
        )
    }

    private val manualInstructions = mapOf(
        "chef" to "Count evil neighbours on a seating ring with the Stowaway's seat removed, even if the Stowaway is dead. Consider Recluse registration and impairment. Show and record your chosen number.",
        "empath" to "Find the two alive neighbours after removing the Stowaway's seat; count their evil alignments, including Siren conversions. Consider Recluse registration and impairment. Show and record your chosen number.",
        "cartographer" to "On their first night, count the shortest seat distance to an evil player after removing the Stowaway's seat (a neighbour is 1). Consider Recluse registration and impairment. Show and record the number.",
        "fortuneteller" to "Collect two choices. If the chooser is good, apply any healthy Wrecker swap to each legal target, silently. Resolve the Demon reading on those targets, with red herring, Recluse and impairment. Show and record the answer; keep the original and resolved choices in your notes.",
        "ravenkeeper" to "Only if they died tonight: collect a choice, apply any legal Wrecker redirect if the chooser is good, then show the resolved target's character (subject to registration and impairment). Record what was shown.",
        "lighthousekeeper" to "Collect a player other than the Keeper; dead players are legal. Apply any legal Wrecker redirect if the Keeper is good. Mark the actual recipient Safe; if the Keeper is impaired there is no protection. Manually prevent the Demon's effect on that player, including Siren conversion and Pukka death; the Pukka victim becomes healthy. At dawn announce where the light actually fell.",
        "harbourmaster" to "During the day, record the public closure and the specific following night it affects; mark Closed permanently to show the ability is spent. Closed is a used-ability marker, not protection for later nights. It is spent even if the Harbourmaster is impaired; in that case the closure fails. A successful closure prevents every death and Siren conversion that night, but not new Pukka poisoning or a Ferryman resurrection.",
        "pearldiver" to "Choose a player who died at night, any night; never an executed player. If none exist, shake your head. A Wrecker swap is legal only if the substitute also died at night. Show the true character, subject to Recluse registration and impairment: a dead Drunk is Drunk, a starpassed Imp is Imp. Record what was shown.",
        "navigator" to "Wake on nights after the first until No ability is marked; they may decline. If used, record the two ordered endpoints, apply legal Wrecker redirects for a good chooser, then count alive evil players strictly between them clockwise, skipping the Stowaway's seat. A Stowaway endpoint is measured from its physical seat. Consider Recluse registration and impairment; show the number and mark No ability.",
        "ferryman" to "Only after a death tonight or during the preceding day, with an unresolved crossing: obtain the choice made at death. Apply a legal Wrecker redirect to a good Ferryman's night choice. If the target is dead and good, and the Ferryman was healthy when they died, resurrect that player now and mark Crossing. A Siren-converted Outsider is evil. Surviving execution does not trigger this. Previously spent abilities stay spent.",
        "stowaway" to "Remove this seat for Chef, Empath, Cartographer and Navigator calculations, even after death. Keep the physical seat for all player choices, voting, majority and kills. A Navigator may choose it as an endpoint.",
        "albatross" to "After execution, even if the Albatross survives, mark Cursed on the nominator if the ability worked. At the start of tonight kill the nominator unless the harbour was successfully closed. Keeper protection does not stop this. Resolve Ferryman and Ravenkeeper triggers from that death.",
        "wrecker" to "Clear the previous pair, choose two players and mark both Wrecked. If the Wrecker is healthy, silently swap a good chooser's nightly target from either mark to the other only when the replacement is legal. Never redirect evil abilities or day choices. Record both marks and each resolved target.",
        "smuggler" to "Wake last. Choose a player and replay exactly what they were shown tonight, including false information, from the information log. Show fingers, tokens or yes/no as appropriate; shake your head if they learned nothing. Do not reveal their character or reminder tokens. If impaired, choose any information. Record what you show.",
        "imp" to "Collect the choice and resolve the kill manually after checking harbour closure, Keeper protection and impairment. Record a Demon death on the seat. If the Imp kills itself by its own ability, choose a Minion to become the Imp and record the character change; the new Imp does not attack again tonight.",
        "pukka" to "Collect tonight's choice and manually poison the new target. Then resolve the PREVIOUS poisoned target's death, checking harbour closure, Keeper protection and impairment; remove the old poison even if the death is prevented. On the first night there is no previous victim. Maintain the poisoned effect on the correct seat for information calculations.",
        "siren" to "Collect a choice. An impaired Siren has no effect. Closed harbour or Keeper protection blocks both killing and conversion and does not spend Sung. Otherwise, the first eligible Outsider chosen becomes evil instead of dying: keep their character, mark Sung, wake them, point at the Siren, show their true token and evil alignment; show the Siren a thumbs-up. A converted Drunk learns they are Drunk. Otherwise record a Demon kill.",
        "kraken" to "Collect one target; if a Minion actually died during the preceding day, optionally collect a second distinct target. Daytime execution without death and night Minion deaths do not qualify. Resolve each kill manually, checking Keeper protection, harbour closure and impairment separately. Remove Thrash afterward.",
    )

    private const val dusk = "Salt & Lantern: confirm whether the harbour was successfully closed for this specific night. The permanent Closed mark records prior use; it does not close the harbour on later nights. Resolve the Albatross's cursed nominator death now, before the Poisoner; closure stops it, Keeper protection does not. Record Ferryman and Ravenkeeper triggers. Mark Thrash only for a Minion who actually died during the day. Clear the previous night's Wrecked and Safe marks before new choices."
    private const val dawn = "Salt & Lantern: announce deaths and Ferryman returns without revealing their cause. If the Lighthouse Keeper acted, announce ‘Last night, the lantern fell on [player]’ using the actual target after redirection. An impaired Keeper's announcement is your choice. Finish and record any Siren conversion privately before opening the day."
    private const val day = "Salt & Lantern: collect a Ferryman's choice when they die. Record a Harbourmaster's public closure, whether it worked at declaration, and the following night it affects; retain Closed as the permanent used mark. After an Albatross execution, record its nominator as Cursed even if execution protection kept the Albatross alive. Mark Kraken Thrash only after a Minion actually dies. These homebrew consequences need manual recording."
    private val notes = """
        Salt & Lantern · v1.1 · 13 Townsfolk / 4 Outsiders / 4 Minions / 4 Demons · 12 homebrews

        Run the homebrews with the supplied rulings. Night rows marked ‘Resolve manually’ supply instructions; use seat controls for reminders, deaths, resurrection, poisoning and alignment changes, and the custom player card for information. Completing a manual row records the wake and completion only. It does not apply its ability. Official abilities whose answers or kills depend on these homebrews are also manual; Poisoner and Devil's Advocate retain their normal controls.

        Setup uses the base distribution: nobody changes the Outsider count. Keep a second seating ring with the Stowaway removed. Review the Jinxes tab before play. Record every piece of information shown so the Smuggler can repeat it exactly. All information depends on impairment and applicable registration rulings.

        First game, 12 players: Chef, Empath, Cartographer, Fortune Teller, Lighthouse Keeper, Pearl Diver, Harbourmaster; Drunk shown Investigator, Stowaway; Wrecker, Smuggler; Siren. Demon bluffs: Slayer, Ravenkeeper, Navigator. The author suggests swapping Siren for Imp for a table new to the script.

        9 players: Empath, Fortune Teller, Cartographer, Pearl Diver, Harbourmaster; Drunk shown Slayer, Albatross; Devil's Advocate; Kraken. Bluffs: Chef, Ravenkeeper, Navigator.

        15 players: Chef, Investigator, Empath, Fortune Teller, Cartographer, Lighthouse Keeper, Pearl Diver, Navigator, Ferryman; Recluse, Stowaway; Poisoner, Wrecker, Devil's Advocate; Pukka. Bluffs: Slayer, Virgin, Harbourmaster.

        $day

        $dusk

        $dawn

        The written v1.1 night order is used, including Pukka before starting information, Ferryman before Ravenkeeper, and Smuggler last. The JSON's original fractional positions and all original reminder text remain in each character reference. For Ferryman choices, the written rules clarify day choices at death and night choices at the Ferryman step; resolve the crossing after the Demon.
    """.trimIndent()
}

/** Minimal wake gates for the supplied homebrews; their actions are intentionally manual. */
internal val SALT_AND_LANTERN_RULES: List<CharacterRule> = listOf(
    CharacterRule("cartographer", firstNight = NightRule(infoId = "")),
    CharacterRule("lighthousekeeper", otherNight = NightRule(infoId = "")),
    CharacterRule("harbourmaster", tokens = listOf(
        TokenRule("harbourmaster", "Closed", effect = EffectKind.SPENT, until = Until.FOREVER),
    )),
    CharacterRule("pearldiver", otherNight = NightRule(infoId = "")),
    CharacterRule("navigator",
        otherNight = NightRule(gate = Gates.all(Gates.aliveHolder, Gates.notSpent()), infoId = ""),
        tokens = listOf(TokenRule("navigator", "No ability", effect = EffectKind.SPENT, until = Until.FOREVER)),
    ),
    CharacterRule(SaltAndLantern.FERRYMAN_ID, actsWhileDead = true, otherNight = NightRule(
        gate = Gates.ask("Did the Ferryman die tonight or during the preceding day, and is their crossing still unresolved?", "Yes — resolve crossing", "No — skip"),
        infoId = "",
    )),
    CharacterRule("stowaway", keepsAbilityWhenDead = true),
    CharacterRule("albatross"),
    CharacterRule("wrecker", firstNight = NightRule(infoId = ""), otherNight = NightRule(infoId = "")),
    CharacterRule("smuggler", firstNight = NightRule(infoId = ""), otherNight = NightRule(infoId = "")),
    CharacterRule("siren", otherNight = NightRule(infoId = "")),
    CharacterRule("kraken", otherNight = NightRule(infoId = "")),
)
