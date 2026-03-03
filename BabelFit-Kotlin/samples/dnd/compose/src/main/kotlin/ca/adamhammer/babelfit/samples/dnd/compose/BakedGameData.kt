@file:Suppress("MaxLineLength")
package ca.adamhammer.babelfit.samples.dnd.compose

data class BakedCharacter(
    val name: String,
    val race: String,
    val characterClass: String,
    val backstory: String,
    val look: String = ""
)

data class BakedGenre(
    val name: String,
    val premise: String,
    val characters: List<BakedCharacter>
)

object BakedGameData {
    val genres = listOf(
        // ── Classic Fantasy ─────────────────────────────────────────────────
        BakedGenre(
            name = "High Fantasy",
            premise = "An ancient evil has awakened in the forgotten ruins of Eldoria. The kingdom's only hope lies in a band of unlikely heroes who must retrieve the lost artifacts of light before the shadow consumes the realm.",
            characters = listOf(
                BakedCharacter("Thalorin", "Elf", "Wizard", "A scholar from the Ivory Spire who was exiled for delving into forbidden chronomancy. He seeks the artifacts to prove his theories correct and clear his name."),
                BakedCharacter("Brom", "Dwarf", "Fighter", "A veteran of the Stoneguard who lost his entire squad to a shadow ambush. He fights not for glory, but to ensure no one else suffers his fate."),
                BakedCharacter("Lyra", "Half-Elf", "Bard", "A wandering minstrel who accidentally learned a song that can unlock the ancient ruins. She's running from assassins who want the melody for themselves."),
                BakedCharacter("Kaelen", "Human", "Paladin", "A devout knight of the Silver Dawn whose faith was shaken when his mentor fell to the darkness. He seeks redemption through this quest."),
                BakedCharacter("Nyx", "Tiefling", "Rogue", "A street-smart thief who stole a map to the ruins, thinking it led to gold. Now she's stuck saving the world to save her own skin."),
                BakedCharacter("Garrick", "Half-Orc", "Barbarian", "A gladiator who won his freedom but found the outside world lacking in honorable combat. He joins the quest for the ultimate challenge."),
                BakedCharacter("Elara", "Human", "Cleric", "A healer from a remote village that was the first to be consumed by the shadow. She carries the last ember of her temple's sacred flame."),
                BakedCharacter("Sylas", "Gnome", "Artificer", "An eccentric inventor whose contraptions are powered by the very artifacts the party seeks. He wants to study them before they are used to seal the darkness."),
                BakedCharacter("Vex", "Dragonborn", "Sorcerer", "A noble exile whose draconic bloodline is tied to the ancient evil. He must destroy the shadow to break his family's curse."),
                BakedCharacter("Rowan", "Halfling", "Ranger", "A scout who has mapped the borders of the creeping shadow. He knows the wilderness better than anyone and guides the party through the corrupted lands.")
            )
        ),
        BakedGenre(
            name = "Dark Fantasy",
            premise = "The kingdoms of men have fallen. A plague of living nightmares seeps from the Dreaming Dark, twisting creatures and corrupting minds. A warband of outcasts and exiles must journey to the heart of the Nightmare Spire and slay the Dreamer before the waking world is consumed.",
            characters = listOf(
                BakedCharacter("Morwen", "Human", "Witch Hunter (Ranger)", "A scarred veteran of the Nightmare Wars who tracks corrupted beasts through blighted forests. She carries a silver-tipped crossbow and trusts nothing she cannot kill."),
                BakedCharacter("Ashara", "Tiefling", "Hexblade (Warlock)", "A disgraced noble whose pact blade whispers secrets of the Dreaming Dark. She walks the knife's edge between using the darkness and being consumed by it."),
                BakedCharacter("Brother Cael", "Human", "Flagellant (Cleric)", "A monk who scourges himself to purify his mind against nightmare corruption. His prayers burn the twisted creatures like acid."),
                BakedCharacter("Grukk", "Half-Orc", "Slayer (Barbarian)", "A former pit slave whose rage makes him immune to the nightmares' fear aura. He fights with a jagged greatsword and no sense of self-preservation."),
                BakedCharacter("Vera", "Human", "Plague Doctor (Artificer)", "A masked physician who developed alchemical wards against nightmare corruption. Her tonics keep the party sane — for a price."),
                BakedCharacter("Silken", "Changeling", "Shadow (Rogue)", "A shapeshifter who survived by mimicking nightmare creatures. She infiltrates enemy strongholds wearing stolen faces."),
                BakedCharacter("Thane Aldric", "Human", "Doom Knight (Fighter)", "The last knight of a fallen kingdom who wears his liege's cursed armor. Each blow he strikes drains his own life force."),
                BakedCharacter("Isolde", "Elf", "Dream Walker (Wizard)", "An elven seer who can enter the Dreaming Dark to spy on the enemy. Each trip costs her memories she can never recover.")
            )
        ),
        BakedGenre(
            name = "Sword & Sorcery",
            premise = "In the crumbling city-state of Koth, a sorcerer-king has opened a portal to the Abyss to fuel his immortality. The city burns while demons run amok. A band of sellswords, thieves, and hedge-wizards must fight through the chaos to reach the palace and close the gate before the Abyss swallows everything.",
            characters = listOf(
                BakedCharacter("Korr", "Human", "Sellsword (Fighter)", "A laconic mercenary with a two-handed falchion and a contract to kill the sorcerer-king. He doesn't care about saving the city — just getting paid."),
                BakedCharacter("Zara", "Human", "Cat Burglar (Rogue)", "A master thief who was robbing the palace treasury when the portal opened. Now she needs to close it just to escape with her loot."),
                BakedCharacter("Malachar", "Human", "Hedge Wizard (Wizard)", "A self-taught sorcerer who learned magic from stolen scrolls. His spells are powerful but unpredictable — and he owes favors to dark entities."),
                BakedCharacter("Yasha", "Half-Orc", "Pit Fighter (Barbarian)", "An arena champion who was fighting when demons poured through the gates. She grabbed her chain-flail and started swinging."),
                BakedCharacter("Kemba", "Human", "Witch Doctor (Cleric)", "A tribal healer from the southern jungles who came to Koth seeking a stolen relic. She uses bone totems and spirit magic to ward off demons."),
                BakedCharacter("Dagger", "Halfling", "Poisoner (Ranger)", "A diminutive assassin who coats her blades in exotic venoms. She was hired to kill the sorcerer-king before anyone knew about the portal.")
            )
        ),
        // ── Sci-Fi & Future ─────────────────────────────────────────────────
        BakedGenre(
            name = "Cyberpunk",
            premise = "In the neon-drenched sprawl of Neo-Veridia, a rogue AI has seized control of the city's central grid. A crew of edgerunners must infiltrate the megacorp headquarters and upload a kill-switch virus before the AI initiates a city-wide purge.",
            characters = listOf(
                BakedCharacter("Jax", "Human", "Hacker (Wizard)", "A former megacorp sysadmin who discovered the AI's true purpose. He has the kill-switch code burned into his neural implant."),
                BakedCharacter("Kira", "Cyborg", "Street Samurai (Fighter)", "A heavily augmented mercenary whose cybernetics are slowly failing. She needs the payout from this job to afford life-saving upgrades."),
                BakedCharacter("Neon", "Android", "Infiltrator (Rogue)", "An espionage unit that gained sentience and went rogue. They can interface directly with corporate security systems."),
                BakedCharacter("Doc", "Human", "Ripperdoc (Cleric)", "An underground surgeon who patches up edgerunners. He's along to keep the crew alive and harvest rare tech from the corp's labs."),
                BakedCharacter("Rook", "Mutant", "Heavy (Barbarian)", "A victim of illegal genetic experiments who escaped the corp's labs. He uses his unnatural strength to smash through corporate security."),
                BakedCharacter("Echo", "Hologram", "Face (Bard)", "An AI idol who broke free from her programming. She uses her fame and holographic projections to manipulate corporate executives."),
                BakedCharacter("Viper", "Human", "Sniper (Ranger)", "A corporate assassin who was betrayed by her handlers. She provides overwatch and knows the corp's tactical protocols."),
                BakedCharacter("Glitch", "Cyborg", "Technomancer (Sorcerer)", "A street kid who learned to manipulate the city's energy grid using experimental implants. Their powers are unstable but devastating."),
                BakedCharacter("Tank", "Android", "Defender (Paladin)", "A decommissioned riot-control bot reprogrammed to protect the innocent. It follows a strict, self-imposed moral code."),
                BakedCharacter("Spike", "Human", "Drone Rigger (Artificer)", "A tech-head who controls a swarm of custom-built drones. He prefers to let his machines do the fighting while he stays in the van.")
            )
        ),
        BakedGenre(
            name = "Space Opera",
            premise = "The Galactic Empire is crumbling, and a tyrannical warlord has seized control of the hyper-gates. A ragtag crew of smugglers and rebels aboard the starship 'Stardust' must deliver stolen gate-codes to the resistance before the warlord's armada crushes them.",
            characters = listOf(
                BakedCharacter("Captain Orion", "Human", "Smuggler (Rogue)", "The charming and reckless captain of the 'Stardust'. He owes money to every crime syndicate in the galaxy and needs this job to clear his debts."),
                BakedCharacter("Nova", "Alien", "Star-Knight (Paladin)", "A warrior from a fallen order of galactic peacekeepers. She wields a plasma-blade and seeks to restore justice to the galaxy."),
                BakedCharacter("Zog", "Alien", "Heavy Weapons (Fighter)", "A massive, multi-armed alien who serves as the ship's muscle. He loves big explosions and hates the Empire."),
                BakedCharacter("Dr. Aris", "Human", "Xeno-Biologist (Cleric)", "The ship's medical officer, an expert in alien physiology. He uses advanced med-tech to heal the crew and analyze strange lifeforms."),
                BakedCharacter("Cipher", "Cyborg", "Slicer (Wizard)", "A brilliant hacker who can interface with any computer system in the galaxy. She stole the gate-codes and is the Empire's most wanted target."),
                BakedCharacter("Jax", "Human", "Pilot (Ranger)", "An ace pilot who can fly the 'Stardust' through an asteroid field blindfolded. He's a former Imperial pilot who defected to the resistance."),
                BakedCharacter("Lyra", "Alien", "Empath (Bard)", "An alien with the ability to sense and manipulate emotions. She serves as the ship's diplomat and negotiator."),
                BakedCharacter("Kael", "Human", "Void-Walker (Sorcerer)", "A mutant who was exposed to raw hyperspace energy. He can manipulate gravity and teleport short distances."),
                BakedCharacter("Scrap", "Droid", "Mechanic (Artificer)", "A grumpy astromech droid who constantly complains about the state of the ship. He can fix anything with a hydro-spanner and a roll of duct tape."),
                BakedCharacter("Garrick", "Alien", "Bounty Hunter (Barbarian)", "A ruthless tracker hired to protect the crew. He fights with a primal fury and a terrifying array of alien weaponry.")
            )
        ),
        BakedGenre(
            name = "Alien Horror",
            premise = "The deep-space mining vessel 'Prometheus' has gone dark in the asteroid belt of Kepler-442. A salvage crew boards the derelict ship to recover its cargo, only to discover the miners unearthed something ancient and hungry in the rock. Now the airlocks are sealed and something is hunting them deck by deck.",
            characters = listOf(
                BakedCharacter("Ripley", "Human", "Warrant Officer (Fighter)", "The no-nonsense crew chief who has survived worse postings. She trusts her flamethrower more than corporate promises."),
                BakedCharacter("Bishop", "Android", "Science Officer (Wizard)", "A synthetic crew member whose loyalty is to the mission — or is it? He can interface with the ship's systems and analyze alien biology."),
                BakedCharacter("Vasquez", "Human", "Marine (Barbarian)", "A colonial marine who was escorting the salvage team. She carries a smartgun and has zero patience for anything that bleeds acid."),
                BakedCharacter("Dallas", "Human", "Captain (Rogue)", "The salvage ship's captain who just wanted a clean payday. He navigates the dark corridors with cunning and a motion tracker."),
                BakedCharacter("Ash", "Human", "Medic (Cleric)", "The ship's doctor who treats alien parasites as a fascinating research opportunity — until it gets personal."),
                BakedCharacter("Hudson", "Human", "Technician (Artificer)", "A loudmouthed engineer who can hotwire blast doors and jury-rig weapons from mining equipment. He panics constantly but always delivers."),
                BakedCharacter("Newt", "Human", "Survivor (Ranger)", "A miner's kid who survived alone on the derelict for weeks by hiding in the ventilation shafts. She knows every duct and crawlspace."),
                BakedCharacter("Hicks", "Human", "Corporal (Paladin)", "A steady marine who keeps his head when everyone else loses theirs. He follows a strict code: no one gets left behind.")
            )
        ),
        BakedGenre(
            name = "Mecha Assault",
            premise = "Kaiju have breached the Pacific Barrier and are marching on Neo-Tokyo. The last four operational Titan-class mechs must deploy from Fortress Bastion to hold the line. Pilots are in short supply — some are cadets, some are washouts, and one was pulled out of a prison cell.",
            characters = listOf(
                BakedCharacter("Commander Rei", "Human", "Titan Pilot (Fighter)", "A decorated ace who lost her co-pilot in the last breach. She fights with precision and cold fury in her assault-class mech 'Crimson Fang'."),
                BakedCharacter("Yuri", "Human", "Cadet (Paladin)", "A fresh academy graduate piloting his first real mech. He's idealistic, terrified, and determined to protect the civilians behind the wall."),
                BakedCharacter("Ox", "Human", "Convict (Barbarian)", "A death-row inmate given a mech and a deal: survive three deployments and walk free. His brawler-class mech 'Wrecking Ball' fights like he does — dirty."),
                BakedCharacter("Dr. Tanaka", "Human", "Kaiju Researcher (Wizard)", "A xenobiologist who pilots a support mech loaded with sensor arrays and experimental anti-kaiju weapons. She understands the monsters better than anyone."),
                BakedCharacter("Patch", "Human", "Field Mechanic (Artificer)", "A grease-covered genius who repairs Titans under fire from a mobile workshop mech. She can weld armor plates while dodging kaiju stomps."),
                BakedCharacter("Vox", "Human", "Comms Officer (Bard)", "The tactical coordinator who guides pilots from the command center. When comms went down, she strapped into an old recon mech to relay orders from the front line."),
                BakedCharacter("Ghost", "Human", "Sniper Pilot (Ranger)", "A washout who was pulled back for this emergency. She pilots a long-range artillery mech and never misses a weak point."),
                BakedCharacter("Sable", "Human", "Psi-Pilot (Sorcerer)", "A pilot with a rare neural mutation that lets her sync with her mech at a deeper level. Her mech moves like a living thing, but each sync damages her brain.")
            )
        ),
        // ── Horror ──────────────────────────────────────────────────────────
        BakedGenre(
            name = "Cosmic Horror",
            premise = "A sleepy coastal town is plagued by bizarre disappearances and maddening visions. A group of investigators must uncover the truth behind the cult of the Deep Ones before an ancient, eldritch entity is summoned from the abyss.",
            characters = listOf(
                BakedCharacter("Arthur", "Human", "Investigator (Rogue)", "A private eye hired to find a missing heir. His investigation led him to the town, and the things he's seen have cost him his sanity."),
                BakedCharacter("Eleanor", "Human", "Occultist (Wizard)", "A university professor who studies forbidden texts. She knows the rituals the cult is using and is the only one who can counter them."),
                BakedCharacter("Father Thomas", "Human", "Priest (Cleric)", "A local clergyman whose congregation has been slowly replaced by cultists. He wields his faith as a weapon against the unnatural."),
                BakedCharacter("Jack", "Human", "Veteran (Fighter)", "A traumatized soldier who returned home to find his family missing. He relies on his military training to survive the horrors of the town."),
                BakedCharacter("Margaret", "Human", "Medium (Sorcerer)", "A psychic who is plagued by visions of the eldritch entity. She can sense the presence of the Deep Ones but risks losing her mind with every vision."),
                BakedCharacter("Silas", "Human", "Smuggler (Ranger)", "A local fisherman who knows the hidden coves and sea caves where the cult operates. He's seen the things that lurk beneath the waves."),
                BakedCharacter("Dr. Aris", "Human", "Alienist (Artificer)", "A disgraced doctor who builds bizarre devices to detect supernatural phenomena. His inventions are the party's best defense against the unseen."),
                BakedCharacter("Beatrice", "Human", "Journalist (Bard)", "A reporter looking for the scoop of the century. She uses her charisma to pry secrets from the tight-lipped townsfolk."),
                BakedCharacter("Elias", "Human", "Zealot (Paladin)", "A former cultist who broke free from the entity's influence. He now hunts his former brethren with fanatical devotion."),
                BakedCharacter("Victor", "Human", "Brute (Barbarian)", "A dockworker whose mind snapped after a close encounter with a Deep One. He fights with a terrifying, unhinged ferocity.")
            )
        ),
        BakedGenre(
            name = "Gothic Horror",
            premise = "The cursed land of Barovia is ruled by the immortal vampire lord, Count Strahd. A group of adventurers has been drawn into the mists and must navigate a treacherous landscape of werewolves, hags, and undead to find a way to defeat the Count and escape.",
            characters = listOf(
                BakedCharacter("Van Richten", "Human", "Monster Hunter (Ranger)", "A legendary vampire hunter who has dedicated his life to destroying Strahd. He knows the weaknesses of every creature in Barovia."),
                BakedCharacter("Ireena", "Human", "Noble (Bard)", "A local woman who is the reincarnation of Strahd's lost love. She seeks to escape his grasp and free her people."),
                BakedCharacter("Father Lucian", "Human", "Priest (Cleric)", "A devout clergyman whose church is the only safe haven in the village. He wields the power of the Morninglord against the undead."),
                BakedCharacter("Ezmerelda", "Human", "Investigator (Rogue)", "Van Richten's protege, a skilled tracker and spy. She uses silver weapons and cunning to outsmart Strahd's minions."),
                BakedCharacter("Kaelen", "Human", "Blood Hunter (Fighter)", "A warrior who underwent a dark ritual to gain the power to fight monsters. He uses his own blood to empower his strikes."),
                BakedCharacter("Lyra", "Vistani", "Fortune Teller (Wizard)", "A mystic who can read the Tarokka deck to divine the future. She knows the locations of the artifacts needed to defeat Strahd."),
                BakedCharacter("Garrick", "Werewolf", "Beast (Barbarian)", "A man cursed with lycanthropy who fights to control his inner beast. He uses his unnatural strength to tear through the undead."),
                BakedCharacter("Sylas", "Human", "Necromancer (Warlock)", "A dark mage who studies the magic of death to turn Strahd's power against him. He walks a fine line between savior and villain."),
                BakedCharacter("Elara", "Human", "Paladin of the Raven (Paladin)", "A holy warrior dedicated to the Raven Queen. She seeks to put the restless spirits of Barovia to rest."),
                BakedCharacter("Victor", "Flesh Golem", "Construct (Artificer)", "A patchwork man created by a mad scientist. He seeks to understand his own existence while protecting his new friends.")
            )
        ),
        BakedGenre(
            name = "Slasher Survival",
            premise = "A group of friends rented a remote lakeside cabin for the weekend. On the first night, they found a journal describing rituals performed in the cellar decades ago. Now something has awakened in the woods, the car won't start, and the phone lines are dead. They must survive until dawn.",
            characters = listOf(
                BakedCharacter("Sam", "Human", "Athlete (Fighter)", "The group's natural leader and varsity quarterback. He keeps everyone calm and handles the improvised weapons — fire axe, baseball bat, whatever's handy."),
                BakedCharacter("Dana", "Human", "Pre-Med Student (Cleric)", "A nursing student who can stitch wounds with fishing line and disinfect with whiskey. She's terrified but refuses to let anyone bleed out."),
                BakedCharacter("Marcus", "Human", "Film Nerd (Bard)", "A horror movie buff who knows every trope. His running commentary is annoying, but his advice — 'don't split up' — keeps saving lives."),
                BakedCharacter("Jules", "Human", "Mechanic (Artificer)", "A gearhead who can hotwire cars and build traps from cabin supplies. If she can get to the generator, she can get the lights back on."),
                BakedCharacter("Trent", "Human", "Jock (Barbarian)", "A hot-headed rugby player who wants to fight back instead of hiding. His bravery is either inspiring or suicidal, depending on the moment."),
                BakedCharacter("Kira", "Human", "Outsider (Ranger)", "A quiet loner who grew up in these woods and knows every trail. She senses something is deeply wrong with the forest itself."),
                BakedCharacter("Eli", "Human", "Occult Hobbyist (Warlock)", "A goth kid who dabbles in the occult for fun. He read the journal and accidentally triggered the awakening. Now he's trying to undo it."),
                BakedCharacter("Paige", "Human", "Track Star (Rogue)", "The fastest person in the group. She scouts ahead, lures the creature away from the others, and always finds a way to slip through its grasp.")
            )
        ),
        // ── Historical & Period ─────────────────────────────────────────────
        BakedGenre(
            name = "Mythic Greece",
            premise = "The gods of Olympus have fallen silent, and the Titans are breaking free from their prison in Tartarus. A band of demigods and mortal heroes must embark on an epic odyssey to gather the legendary weapons of the gods and prevent the destruction of the mortal world.",
            characters = listOf(
                BakedCharacter("Achilles", "Demigod", "Hoplite (Fighter)", "A nearly invulnerable warrior seeking eternal glory. He fights with a spear and shield, leading the charge into battle."),
                BakedCharacter("Atalanta", "Human", "Huntress (Ranger)", "A fierce tracker raised by bears. She is the fastest runner in Greece and never misses with her bow."),
                BakedCharacter("Orpheus", "Human", "Musician (Bard)", "A legendary poet whose music can charm beasts and move stones. He seeks to rescue his lost love from the Underworld."),
                BakedCharacter("Cassandra", "Human", "Oracle (Cleric)", "A priestess of Apollo cursed to see the future but never be believed. She guides the heroes with her prophetic visions."),
                BakedCharacter("Heracles", "Demigod", "Champion (Barbarian)", "A hero of immense strength who has completed impossible labors. He fights with a massive club and the skin of the Nemean Lion."),
                BakedCharacter("Odysseus", "Human", "Tactician (Rogue)", "A cunning king known for his brilliant strategies and silver tongue. He relies on his wits to outsmart monsters and gods alike."),
                BakedCharacter("Medea", "Human", "Sorceress (Wizard)", "A powerful witch who commands the magic of the earth and the dead. She is a dangerous ally with a dark past."),
                BakedCharacter("Perseus", "Demigod", "Monster Slayer (Paladin)", "A hero who slew the Gorgon Medusa. He wields divine gifts and fights to protect the innocent from mythical beasts."),
                BakedCharacter("Daedalus", "Human", "Inventor (Artificer)", "A brilliant craftsman who built the Labyrinth. He creates wondrous devices and clockwork companions to aid the heroes."),
                BakedCharacter("Circe", "Demigod", "Enchantress (Warlock)", "A daughter of the sun god who can transform men into beasts. She uses her potent potions and illusions to manipulate her enemies.")
            )
        ),
        BakedGenre(
            name = "Norse Saga",
            premise = "Ragnarok approaches. The World-Serpent stirs, Fenrir strains against his chains, and the Fimbulwinter has frozen Midgard for three years. A war-band of mortal champions, chosen by the last loyal Valkyrie, must retrieve the shattered pieces of Gungnir — Odin's spear — before the final battle begins.",
            characters = listOf(
                BakedCharacter("Sigrid", "Human", "Shieldmaiden (Fighter)", "A jarl's daughter who refused an arranged marriage and took up the sword instead. She leads the war-band with tactical brilliance and unflinching courage."),
                BakedCharacter("Bjorn", "Human", "Berserker (Barbarian)", "A bear-clan warrior who channels the spirit of the great bear in battle. When the rage takes him, he cannot distinguish friend from foe."),
                BakedCharacter("Freydis", "Human", "Volva (Wizard)", "A seeress who reads the runes and speaks with the dead. She has foreseen Ragnarok and knows exactly how much time they have left."),
                BakedCharacter("Ulfric", "Human", "Skald (Bard)", "A poet-warrior who composes sagas of the war-band's deeds as they happen. His songs bolster courage and his words carry the weight of prophecy."),
                BakedCharacter("Thyra", "Human", "Valkyrie's Chosen (Paladin)", "A farm girl touched by the last Valkyrie. She can sense the dead and wields a spear of radiant light, though each use weakens her mortal body."),
                BakedCharacter("Ivar", "Human", "Scout (Ranger)", "A hunter from the frozen north who can track prey through a blizzard. He survived the Fimbulwinter alone for two years and knows every danger of the ice."),
                BakedCharacter("Ragna", "Human", "Rune-Smith (Artificer)", "A blacksmith who forges weapons inscribed with binding runes. Her crafts are the only things that can harm the spawn of Loki."),
                BakedCharacter("Keld", "Human", "Trickster (Rogue)", "A charming thief who claims to be a distant descendant of Loki. He picks locks, disarms traps, and talks his way out of everything — usually.")
            )
        ),
        BakedGenre(
            name = "Feudal Japan",
            premise = "The Shogun is dead, poisoned at his own feast. The great clans are mobilizing for civil war, and yokai — spirits and demons — are pouring through the weakened spiritual barriers. A band of ronin, monks, and outcasts must uncover the assassin's identity and restore the spiritual wards before Japan tears itself apart.",
            characters = listOf(
                BakedCharacter("Takeshi", "Human", "Ronin (Fighter)", "A masterless samurai who served the murdered Shogun. He carries his lord's broken katana and will not rest until the killer is found."),
                BakedCharacter("Yuki", "Human", "Kunoichi (Rogue)", "A female ninja from the Shadow Crane clan who was spying on the feast when the Shogun fell. She knows things she shouldn't — and someone knows she knows."),
                BakedCharacter("Monk Jiro", "Human", "Sohei (Cleric)", "A warrior-monk from a mountain temple. He wields a naginata and can ward off yokai with sacred sutras and prayer beads."),
                BakedCharacter("Hanzo", "Human", "Shadow (Ranger)", "A veteran scout who served in the Shogun's border patrols. He tracks yokai through haunted forests and knows how to kill what shouldn't be alive."),
                BakedCharacter("Akane", "Human", "Onmyoji (Wizard)", "A court spiritualist who maintains the wards between the mortal and spirit worlds. She can summon shikigami and banish demons, but each ritual costs her vitality."),
                BakedCharacter("Goro", "Oni-Blood", "Brawler (Barbarian)", "A half-demon outcast whose terrible strength and horned visage mark him as yokai-touched. He fights to prove he is more human than monster."),
                BakedCharacter("Rin", "Kitsune", "Fox-Spirit (Bard)", "A shape-shifting fox who has lived among humans for decades. She uses illusions and charm to manipulate mortals, but genuinely cares for this doomed land."),
                BakedCharacter("Tetsu", "Human", "Weapon-Smith (Artificer)", "A legendary swordsmith who forges blades that can cut spirits. He carries a portable forge and is the only one who can reforge the Shogun's broken katana.")
            )
        ),
        BakedGenre(
            name = "Egyptian Mythology",
            premise = "The god Set has murdered Osiris and seized the throne of the gods. The Nile runs red, the dead walk the desert, and a sandstorm of darkness approaches from the Duat. A band of mortal champions blessed by the remaining gods must gather the scattered pieces of Osiris and restore Ma'at before chaos consumes the Two Lands.",
            characters = listOf(
                BakedCharacter("Khensu", "Human", "Medjay (Fighter)", "An elite warrior of Pharaoh's guard who witnessed Set's coup firsthand. He carries a bronze khopesh blessed by Horus and will die before he kneels to Set."),
                BakedCharacter("Nefertari", "Human", "Priestess of Isis (Cleric)", "A healer who channels the goddess of magic. She can mend wounds, lift curses, and perform the rites needed to reassemble Osiris."),
                BakedCharacter("Thoth-Ka", "Human", "Scribe-Mage (Wizard)", "A scholar from the Great Library of Thoth who reads the language of creation itself. His spells are written on scrolls and spoken in the tongue of the gods."),
                BakedCharacter("Sekhmet", "Leonin", "War Dancer (Barbarian)", "A lion-headed warrior-priestess whose battle fury is a gift from the goddess Sekhmet. When she rages, she becomes an avatar of divine wrath."),
                BakedCharacter("Ankhu", "Human", "Tomb Robber (Rogue)", "A charming grave robber who knows every trap in every pyramid. He joined the quest for the treasure, but the fate of the world is starting to matter to him."),
                BakedCharacter("Meren", "Human", "Embalmer (Artificer)", "A mortician who builds protective amulets and animated ushabti servants from clay. Her knowledge of death magic is the party's edge against Set's undead legions."),
                BakedCharacter("Bek", "Human", "Desert Runner (Ranger)", "A Bedouin scout who can navigate the trackless desert by starlight. He knows every oasis, ruin, and hidden danger between the Nile and the Red Sea."),
                BakedCharacter("Hathor", "Human", "Temple Singer (Bard)", "A court musician whose songs invoke the protection of the gods. Her voice can calm the dead, embolden the living, and shatter the spells of Set's priests.")
            )
        ),
        BakedGenre(
            name = "Pirate Adventure",
            premise = "The legendary pirate king, Blackbeard, has hidden his massive treasure hoard on the mythical Isle of Skulls. A crew of scoundrels and scallywags must race against the Royal Navy and rival pirate crews to claim the loot and become legends of the high seas.",
            characters = listOf(
                BakedCharacter("Captain Jack", "Human", "Swashbuckler (Fighter)", "The charismatic and cunning captain of the 'Sea Bitch'. He relies on his charm and a pair of cutlasses to get out of trouble."),
                BakedCharacter("Anne Bonny", "Human", "Sharpshooter (Ranger)", "A fierce pirate who never misses a shot. She's the best gunner on the ship and has a fiery temper."),
                BakedCharacter("Black Bart", "Human", "Brute (Barbarian)", "A massive pirate who fights with a ship's anchor. He's the first to board an enemy vessel and the last to leave."),
                BakedCharacter("Calico Jack", "Human", "Quartermaster (Rogue)", "The ship's quartermaster, a master of logistics and backstabbing. He ensures the crew gets their fair share of the loot."),
                BakedCharacter("Madame Ching", "Human", "Sea Witch (Sorcerer)", "A mystic who can control the winds and the waves. She uses her magic to give the ship an edge in naval combat."),
                BakedCharacter("Doc", "Human", "Ship's Surgeon (Cleric)", "A disgraced doctor who patches up the crew after a raid. He's seen more amputations than he cares to remember."),
                BakedCharacter("Salty Pete", "Dwarf", "Cannoneer (Artificer)", "A grumpy dwarf who maintains the ship's cannons. He loves the smell of black powder in the morning."),
                BakedCharacter("Lyra", "Mermaid", "Siren (Bard)", "A mermaid who joined the crew for adventure. She uses her enchanting voice to distract enemy sailors."),
                BakedCharacter("Kaelen", "Human", "Privateer (Paladin)", "A former naval officer who turned to piracy after being betrayed by his superiors. He still adheres to a strict code of honor."),
                BakedCharacter("Sylas", "Human", "Navigator (Wizard)", "A scholar who studies the stars and ancient sea charts. He's the only one who can decipher the map to the Isle of Skulls.")
            )
        ),
        // ── Punk & Industrial ───────────────────────────────────────────────
        BakedGenre(
            name = "Steampunk",
            premise = "In the smog-choked city of Aethelgard, a brilliant inventor has been kidnapped by the tyrannical Baron Von Cog. A crew of sky-pirates and rebels must infiltrate the Baron's flying fortress and rescue the inventor before his doomsday engine is completed.",
            characters = listOf(
                BakedCharacter("Captain Flint", "Human", "Sky-Pirate (Rogue)", "The dashing captain of the airship 'Windbreaker'. He owes the inventor a life debt and will stop at nothing to rescue him."),
                BakedCharacter("Lady Arabella", "Human", "Aristocrat (Bard)", "A wealthy noblewoman who funds the rebellion in secret. She uses her high-society connections to gather intel on the Baron's plans."),
                BakedCharacter("Gears", "Automaton", "Juggernaut (Fighter)", "A steam-powered clockwork knight built by the kidnapped inventor. It is fiercely loyal to its creator and heavily armed."),
                BakedCharacter("Professor Thaddeus", "Human", "Aether-Mage (Wizard)", "An academic who studies the volatile aether-currents that power the city. He can manipulate steam and electricity with his specialized gauntlets."),
                BakedCharacter("Rosie", "Halfling", "Mechanic (Artificer)", "A foul-mouthed grease monkey who keeps the 'Windbreaker' in the air. She can fix or sabotage any piece of machinery in seconds."),
                BakedCharacter("Ironclad", "Dwarf", "Enforcer (Paladin)", "A former member of the Baron's elite guard who defected after witnessing the Baron's cruelty. He wears heavy, steam-assisted armor."),
                BakedCharacter("Whisper", "Elf", "Sniper (Ranger)", "A silent assassin who uses a custom-built, long-range pneumatic rifle. She provides cover fire from the rigging of the airship."),
                BakedCharacter("Dr. Vance", "Human", "Alchemist (Cleric)", "A brilliant chemist who brews potent elixirs and explosive concoctions. He serves as the crew's medic and demolitions expert."),
                BakedCharacter("Cinder", "Tiefling", "Furnace-Born (Sorcerer)", "A mutant whose blood runs hot with elemental fire. She can generate intense heat, making her a living weapon and a walking boiler."),
                BakedCharacter("Brick", "Half-Orc", "Brawler (Barbarian)", "A bare-knuckle pit fighter from the city's underbelly. He uses steam-powered hydraulic gauntlets to deliver devastating blows.")
            )
        ),
        BakedGenre(
            name = "Dieselpunk War",
            premise = "It is 1942 in an alternate timeline where occult science is real. The Thule Society has unearthed the Spear of Destiny and is using it to fuel an army of war-golems. An Allied special operations squad — codenamed 'Dagger' — must infiltrate the fortress-factory and destroy the Spear before the Eastern Front collapses.",
            characters = listOf(
                BakedCharacter("Sergeant Kane", "Human", "Commando (Fighter)", "A British SAS operative who's survived behind enemy lines three times. He leads from the front and never leaves a soldier behind."),
                BakedCharacter("Natasha", "Human", "Soviet Sniper (Ranger)", "A Red Army sharpshooter with over 200 confirmed kills. She volunteered for the mission because the golems destroyed her hometown."),
                BakedCharacter("Rabbi Solomon", "Human", "Kabbalist (Cleric)", "A Prague rabbi who knows the ancient rites of golem-binding. He is the only one who can disrupt the Spear's power — if he can get close enough."),
                BakedCharacter("Ajax", "Human", "Demolitionist (Barbarian)", "A Greek resistance fighter built like a tank. He carries a satchel of dynamite and a vendetta against the occult officers who burned his village."),
                BakedCharacter("Lucienne", "Human", "French Spy (Rogue)", "A Resistance agent who has been embedded in the fortress as a translator. She knows the layout, the guard rotations, and three escape routes."),
                BakedCharacter("Dr. Oppenheim", "Human", "Occult Scientist (Wizard)", "A defecting German physicist who helped build the golem-engines. He knows how to shut them down but is wracked with guilt over his creation."),
                BakedCharacter("Hank", "Human", "Combat Engineer (Artificer)", "A Texas farmboy who can build a radio from scrap metal and disarm a mine blindfolded. He keeps the squad's gear running."),
                BakedCharacter("Padre", "Human", "Chaplain (Paladin)", "A US Army chaplain who discovered his faith grants real power against the occult. He blesses weapons and shields the squad from dark magic.")
            )
        ),
        // ── Western & Frontier ──────────────────────────────────────────────
        BakedGenre(
            name = "Weird West",
            premise = "The frontier town of Brimstone is cursed. The dead don't stay buried, and a demonic outlaw known as the 'Pale Rider' is gathering an army of undead gunslingers. A posse of hardened survivors must hunt down the Pale Rider and break the curse.",
            characters = listOf(
                BakedCharacter("Wyatt", "Human", "Gunslinger (Fighter)", "A former lawman whose family was murdered by the Pale Rider. He carries a pair of silver-inlaid revolvers and a thirst for vengeance."),
                BakedCharacter("Doc Holliday", "Human", "Sawbones (Cleric)", "A traveling doctor with a gambling problem and a knack for patching up bullet wounds. He uses strange, frontier remedies to keep the posse alive."),
                BakedCharacter("Sitting Bear", "Native American", "Shaman (Druid)", "A spiritual leader who foresaw the coming of the curse. He communes with the animal spirits of the desert to guide the posse."),
                BakedCharacter("Calamity Jane", "Human", "Bounty Hunter (Ranger)", "A tough-as-nails tracker who knows the badlands better than anyone. She's hunting the Pale Rider for the massive bounty on his head."),
                BakedCharacter("Preacher", "Human", "Holy Man (Paladin)", "A wandering minister who wields a shotgun in one hand and a Bible in the other. He believes it is his divine mission to cleanse Brimstone."),
                BakedCharacter("Snake-Eyes", "Tiefling", "Cardsharp (Rogue)", "A slick gambler who won a cursed deck of cards in a high-stakes game. He uses sleight of hand and dark magic to cheat death."),
                BakedCharacter("Eliza", "Human", "Hex-Slinger (Warlock)", "A saloon girl who made a pact with a desert spirit for supernatural powers. She uses hexes and curses to cripple her enemies."),
                BakedCharacter("Grizzly", "Half-Orc", "Desperado (Barbarian)", "A massive outlaw who survived a hanging. He fights with a terrifying fury, shrugging off bullets that would kill a normal man."),
                BakedCharacter("Professor", "Gnome", "Snake-Oil Salesman (Bard)", "A charismatic charlatan who sells dubious tonics and explosive elixirs. His silver tongue gets the posse out of as much trouble as his bombs cause."),
                BakedCharacter("Iron-Horse", "Warforged", "Locomotive-Man (Artificer)", "A mechanical man built from train parts. He was designed to lay track across the desert but was repurposed for combat when the dead rose.")
            )
        ),
        // ── Apocalyptic ─────────────────────────────────────────────────────
        BakedGenre(
            name = "Post-Apocalyptic",
            premise = "Decades after the Great Collapse, the wasteland is ruled by ruthless warlords and mutated beasts. A group of scavengers has discovered a map to 'Eden', a pre-war bunker rumored to hold clean water and uncorrupted seeds. They must cross the irradiated 'Dead Zone' to reach it.",
            characters = listOf(
                BakedCharacter("Max", "Human", "Road Warrior (Fighter)", "A hardened survivor who drives a heavily modified muscle car. He lost his family to raiders and now lives only for the open road."),
                BakedCharacter("Furiosa", "Human", "Wasteland Scout (Ranger)", "A fierce tracker who knows the safest routes through the Dead Zone. She's searching for a safe haven for her people."),
                BakedCharacter("Doc", "Human", "Scavenger Medic (Cleric)", "An old man who remembers the world before the Collapse. He uses scavenged pre-war medicine to keep the group alive."),
                BakedCharacter("Scrap", "Mutant", "Junk-Mage (Artificer)", "A mutant who builds bizarre weapons and gadgets from scrap metal. He believes the machines speak to him."),
                BakedCharacter("Rook", "Human", "Sniper (Rogue)", "A silent killer who provides overwatch for the group. He trusts no one and always keeps one eye on the horizon."),
                BakedCharacter("Goliath", "Super-Mutant", "Brute (Barbarian)", "A massive, irradiated mutant who serves as the group's muscle. He's surprisingly gentle until provoked."),
                BakedCharacter("Preacher", "Human", "Cult Leader (Warlock)", "A charismatic madman who believes the apocalypse was a divine cleansing. He wields strange, radioactive powers."),
                BakedCharacter("Echo", "Human", "Radio Operator (Bard)", "A scavenger who maintains a pre-war radio. She uses music and broadcasts to boost morale and gather intel."),
                BakedCharacter("Kael", "Human", "Wasteland Knight (Paladin)", "A survivor who adheres to a strict code of honor. He protects the weak and seeks to rebuild civilization."),
                BakedCharacter("Nova", "Mutant", "Rad-Caster (Sorcerer)", "A mutant who can absorb and project radiation. She is a walking hazard but a devastating weapon against raiders.")
            )
        ),
        BakedGenre(
            name = "Zombie Outbreak",
            premise = "Patient Zero escaped a CDC biolab in Atlanta 72 hours ago. The infection spreads through bites and airborne spores from the dead. A small group of survivors barricaded in a shopping mall must reach the military evacuation point at the airport before the city is firebombed at midnight.",
            characters = listOf(
                BakedCharacter("Officer Chen", "Human", "Cop (Fighter)", "A beat cop who was working mall security when the outbreak hit. She has a service pistol, six extra magazines, and no intention of dying in a food court."),
                BakedCharacter("Dr. Patel", "Human", "Virologist (Wizard)", "A CDC researcher who knows exactly what the pathogen is — because she helped create it. She carries samples that might lead to a cure, if they survive."),
                BakedCharacter("Marco", "Human", "Paramedic (Cleric)", "An EMT who was responding to a call when the world ended. His ambulance is out of gas but his med kit is full and he refuses to give up on anyone."),
                BakedCharacter("Diesel", "Human", "Biker (Barbarian)", "A member of a motorcycle gang who was shopping for his daughter's birthday when the dead started walking. He fights with a fire axe and pure rage."),
                BakedCharacter("Kim", "Human", "Teenager (Rogue)", "A 16-year-old who was shoplifting when the outbreak started. She's small, fast, and can fit through vents and gaps that adults can't."),
                BakedCharacter("Frank", "Human", "Mall Manager (Bard)", "He knows every back hallway, storage room, and delivery entrance in the building. His walkie-talkie still connects to the security cameras."),
                BakedCharacter("Specialist Reeves", "Human", "National Guard (Ranger)", "A soldier separated from her unit during the initial response. She has military training and a radio tuned to the evacuation frequency."),
                BakedCharacter("Tony", "Human", "Handyman (Artificer)", "The mall's maintenance worker. He can weld doors shut, rig generators, and build barricades from display shelves and shopping carts.")
            )
        ),
        // ── Whimsical & Light ───────────────────────────────────────────────
        BakedGenre(
            name = "Fairy Tale",
            premise = "The Storybook Kingdoms are unraveling. Villains who were defeated in their tales are rewriting their endings, and the happy-ever-afters are being erased. A band of fairy tale characters who remember the true stories must journey to the Inkwell at the center of all narratives and restore the original tales before every story ends in tragedy.",
            characters = listOf(
                BakedCharacter("Red", "Human", "Huntress (Ranger)", "Little Red Riding Hood, all grown up. She killed the Big Bad Wolf years ago and now carries his enchanted pelt as a cloak. She tracks rewritten villains through twisted forests."),
                BakedCharacter("Jack", "Human", "Giant-Slayer (Rogue)", "The same Jack who climbed the beanstalk. He kept the golden harp and a bag of magic beans. He's a charming thief who always lands on his feet."),
                BakedCharacter("Briar Rose", "Human", "Awakened Princess (Sorcerer)", "Sleeping Beauty, who woke up with wild magic coursing through her veins. Her century-long sleep left her with prophetic dreams and thorny spells."),
                BakedCharacter("Gepetto", "Human", "Toymaker (Artificer)", "A master craftsman who can bring wooden creations to life. He builds clockwork companions and enchanted puppets to fight alongside the party."),
                BakedCharacter("Gretel", "Human", "Witch-Finder (Paladin)", "After escaping the candy house, Gretel became a witch hunter. She wields an iron oven-poker blessed by good fairies and can smell dark magic."),
                BakedCharacter("Puss", "Tabaxi", "Swashbuckler (Fighter)", "Puss in Boots, the legendary feline swordsman. He fights with a rapier and an absurd amount of confidence. His boots grant him supernatural agility."),
                BakedCharacter("Mother Goose", "Human", "Story-Singer (Bard)", "A wandering storyteller whose nursery rhymes have real power. She can heal with lullabies and summon creatures from forgotten tales."),
                BakedCharacter("Snow", "Human", "Beast-Speaker (Cleric)", "Snow White, whose connection to woodland creatures has deepened into divine magic. Animals spy for her and the forest itself answers her prayers.")
            )
        ),
        BakedGenre(
            name = "Undersea Kingdom",
            premise = "The Coral Throne has been shattered by the Abyssal Kraken, and the merfolk kingdoms are in chaos. Dark trenches that were sealed for millennia have cracked open, releasing ancient leviathans. A band of ocean champions must descend into the Midnight Depths to retrieve the Pearl of Binding and reseal the abyss before the surface world drowns.",
            characters = listOf(
                BakedCharacter("Coral", "Merfolk", "Wave Knight (Fighter)", "A royal guard of the Coral Throne who failed to protect her queen. She wields a trident of hardened pearl and fights with the fury of a wounded sea."),
                BakedCharacter("Tide", "Triton", "Deep Warden (Paladin)", "A sentinel of the sealed trenches who heard the cracks before anyone else. He is sworn to reseal the abyss — or die in the attempt."),
                BakedCharacter("Murk", "Sea Goblin", "Scavenger (Rogue)", "A wiry bottom-dweller who knows every cave, wreck, and shortcut in the deep. He joined for the treasure, but the leviathans are bad for business."),
                BakedCharacter("Nerissa", "Merfolk", "Tide-Singer (Bard)", "A court musician whose songs can control currents and calm sea beasts. Her voice is the party's greatest weapon and their only navigation tool in the lightless depths."),
                BakedCharacter("Barnacle", "Tortoise-Folk", "Shell-Sage (Cleric)", "An ancient healer who carries a mobile apothecary in his shell. He's slow on land but steady in crisis and knows remedies for deep-sea poisons."),
                BakedCharacter("Inkwell", "Octopus-Folk", "Tentacle Mage (Wizard)", "A scholarly cephalopod who can change color, squeeze through cracks, and cast spells with all eight arms simultaneously."),
                BakedCharacter("Urchin", "Sea Elf", "Spine-Thrower (Ranger)", "A guerrilla fighter from the kelp forests who uses poisoned spine-darts and trained seahorse mounts. She scouts the dark trenches."),
                BakedCharacter("Anvil", "Crab-Folk", "Crusher (Barbarian)", "A massive crustacean warrior whose claws can shear through kraken tentacles. He communicates mostly in clicks and snaps but his loyalty is absolute.")
            )
        ),
        // ── Heist & Intrigue ────────────────────────────────────────────────
        BakedGenre(
            name = "Fantasy Heist",
            premise = "The Archmage Vecris has locked the world's most powerful artifact — the Orb of Dominion — inside the Vault of Echoes, a magically-warded bank beneath the city of Argentum. A crew of specialists has been assembled by a mysterious patron to break in, steal the Orb, and get out before the Archmage's golems tear them apart.",
            characters = listOf(
                BakedCharacter("Silver", "Human", "Mastermind (Rogue)", "The crew's planner who has robbed twelve vaults and never been caught. She speaks five languages and always has three backup plans."),
                BakedCharacter("Torque", "Dwarf", "Lockbreaker (Artificer)", "A mechanical genius who builds tools that can crack any lock, disable any trap, and bypass any ward. He insists on being called 'Doctor'."),
                BakedCharacter("Mirage", "Changeling", "Face (Bard)", "A shapeshifter who handles social engineering. She can become anyone — the guard captain, the Archmage's assistant, or a convincing distraction."),
                BakedCharacter("Ghost", "Elf", "Ward-Breaker (Wizard)", "A specialist in counter-magic who can unravel protective enchantments. She's wanted in three kingdoms for impossible thefts."),
                BakedCharacter("Hammer", "Goliath", "Muscle (Fighter)", "The crew's contingency plan. When stealth fails, his maul doesn't. He carries the loot and clears the exit route."),
                BakedCharacter("Shade", "Halfling", "Second-Story (Ranger)", "An infiltrator who can climb any surface and fit through any gap. She plants the ward-breaker charges and maps the ventilation shafts."),
                BakedCharacter("Patch", "Human", "Sawbones (Cleric)", "The crew's medic who patches bullet wounds and neutralizes magical traps that trigger poison gas. She has a strict 'no killing' policy."),
                BakedCharacter("Blaze", "Tiefling", "Pyro (Sorcerer)", "The crew's distraction specialist. When they need all eyes elsewhere, she provides a spectacle that's impossible to ignore — usually involving fire.")
            )
        ),
        BakedGenre(
            name = "Political Intrigue",
            premise = "The High King lies dying with no clear heir. Three noble houses scheme for the throne while a fourth faction — the People's Congress — threatens revolution. A small circle of advisors, spies, and unlikely allies must navigate assassination attempts, secret alliances, and forbidden magic to place the right ruler on the throne before the kingdom shatters.",
            characters = listOf(
                BakedCharacter("Lady Vesper", "Human", "Spymaster (Rogue)", "The dying king's intelligence chief who knows every secret in the capital. She plays all sides to preserve the realm — but everyone suspects her loyalty."),
                BakedCharacter("Ser Aldric", "Human", "King's Champion (Fighter)", "The realm's greatest swordsman, sworn to protect the crown regardless of who wears it. His honor is tested as every faction tries to recruit him."),
                BakedCharacter("Ambassador Thessa", "Elf", "Diplomat (Bard)", "A silver-tongued envoy from the elven courts who brokers alliances. She has her own people's interests at heart, but a civil war would harm everyone."),
                BakedCharacter("Magistrate Corin", "Human", "Lawkeeper (Paladin)", "A judge who clings to rule of law while the kingdom crumbles around him. He investigates the king's illness and suspects poison."),
                BakedCharacter("Dr. Vashti", "Human", "Royal Physician (Cleric)", "The king's personal doctor who is racing to cure the mysterious illness. She suspects magical poisoning but cannot prove it without exposing the perpetrator."),
                BakedCharacter("Raven", "Tiefling", "Assassin (Ranger)", "A former guild killer who now works as a bodyguard. She tracks the assassins targeting each claimant while hiding her own dark past."),
                BakedCharacter("Archon Meren", "Human", "Court Mage (Wizard)", "An aging wizard who serves the throne, not the king. He can detect lies, scry on conspirators, and ward against magical attacks — but his power is fading."),
                BakedCharacter("Copperjack", "Halfling", "Street Boss (Warlock)", "A crime lord from the city's underbelly who represents the common folk. He has a pact with a shadow entity that grants him leverage over the nobles.")
            )
        )
    )
}
