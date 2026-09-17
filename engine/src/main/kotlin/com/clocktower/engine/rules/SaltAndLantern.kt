package com.clocktower.engine.rules

import com.clocktower.engine.*

/** The supplied script with the v1.2 Ferryman correction and v1.3 Wrecker restriction. The written night sheet takes precedence over JSON indices. */
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
            storytellerNotes = notes,
            duskReminder = dusk,
            dawnReminder = dawn,
            dayReminder = day,
        )
    }

    private const val dusk = "Salt & Lantern: harbour closure applies only to this night. The cursed nominator's Albatross death resolves automatically before the Poisoner. Check any Ferryman crossing caused by that death. Wrecked and Safe marks last one night. Wrecker cannot choose an actual Demon character, even dead; other living or dead players are legal."
    private const val dawn = "Salt & Lantern: announce deaths and Ferryman returns without revealing their cause. Read the lantern fell announcement for the actual recipient. Complete private Siren conversion cards before opening the day."
    private const val day = "Salt & Lantern: use Day abilities to record the Harbourmaster's public closure and a Ferryman's choice at death. An Albatross execution marks its nominator Cursed, even if execution protection prevents death. A daytime Minion death enables Kraken's second target."
    private val notes = """
        Salt & Lantern · v1.3 · 13 Townsfolk / 4 Outsiders / 4 Minions / 4 Demons · 12 homebrews

        v1.3 restricts Wrecker choices: neither chosen player may have an actual Demon character, even if dead. Other living or dead players remain legal; use actual character at the time of choice, not alignment or possible registration. Recheck current identities on each choice, including after an Imp starpass. There is no new living-only restriction or target rotation rule.

        The v1.2 Ferryman targeting rule is unchanged: directly choose another dead player, good or evil. If nobody else is dead, there is no crossing. The new Wrecker jinx, when the Wrecker's ability is working, redirects a good Ferryman's legal night choice back to the dead Ferryman when both are Wrecked, allowing self-resurrection with normal alignment and healthy-at-death checks. Day choices are never redirected. There is no once-per-game restriction.

        Night rows compute information and apply choices, redirects, protection, poisoning, deaths, resurrection and alignment changes when resolved. Use the usual player pickers, show cards and completion controls. Custom information remains available; the exact cards shown are saved for the Smuggler. Day abilities record public harbour closure and Ferryman choices.

        Setup uses the base distribution: nobody changes the Outsider count. The engine omits the Stowaway from the four positional calculations while their ability works, including after death. Review the Jinxes tab before play. Record every piece of information shown so the Smuggler can repeat it exactly. All information depends on impairment and applicable registration rulings.

        First game, 12 players: Chef, Empath, Cartographer, Fortune Teller, Lighthouse Keeper, Pearl Diver, Harbourmaster; Drunk shown Investigator, Stowaway; Wrecker, Smuggler; Siren. Demon bluffs: Slayer, Ravenkeeper, Navigator. The author suggests swapping Siren for Imp for a table new to the script.

        9 players: Empath, Fortune Teller, Cartographer, Pearl Diver, Harbourmaster; Drunk shown Slayer, Albatross; Devil's Advocate; Kraken. Bluffs: Chef, Ravenkeeper, Navigator.

        15 players: Chef, Investigator, Empath, Fortune Teller, Cartographer, Lighthouse Keeper, Pearl Diver, Navigator, Ferryman; Recluse, Stowaway; Poisoner, Wrecker, Devil's Advocate; Pukka. Bluffs: Slayer, Virgin, Harbourmaster.

        $day

        $dusk

        $dawn

        The written v1.1 night order is used, including Pukka before starting information, Ferryman before Ravenkeeper, and Smuggler last. The JSON's original fractional positions remain; Ferryman ability and reminder text include the v1.2 direct-self-choice restriction and Wrecker exception; Wrecker text includes the v1.3 Demon-target exclusion. For Ferryman choices, the written rules clarify day choices at death and night choices at the Ferryman step; resolve the crossing after the Demon.
    """.trimIndent()
}

/** The bundled homebrews use the same action and effect interpreter as the official characters. */
internal val SALT_AND_LANTERN_RULES: List<CharacterRule> = SaltAndLanternAutomation.rules()
