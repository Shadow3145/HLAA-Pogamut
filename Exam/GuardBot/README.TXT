IMPLEMENTED BEHAVIOURS

1) This behaviour has the highest priority. If the bot doesn't have Flak Cannon he immediately goes get one.
2) This behaviour has the second highest priority. If the bot doesn't have ammo, he immediately goes get some.
3) If the bot doesn't have a civilian to lead to safety or he doesn't have much ammo left he goes for it. He doesn't go for nearby ammo if he sees some enemy.
4) If there is some ammo nearby or he doesn't have much ammo he tells the civilian to stop an goes get the ammo.
5) If the bot sees some enemy (he selects the one nearest to nearest civilian, in case of no civilian nearby, he targets the enemy nearest to him) he gets closer to him (around 350 units) and fires from the flak cannon at the enemy. This most of the time works well – if he sees the enemy soon enough.
6) The bot picks the civilian that is nearest to him, goes to him and then leads him to the factory – the bot goes in front of the civilian and he uses the civilian's speed. If the civilian stops following him for any reason, the bot goes to the last known location of the civilian to resolve the issue. 
7) There is a cooldown for scanning the area – turning around. If he sees an enemy that is very close to him he shoots him immediately. Otherwise he saves his location. After the scanning is done he starts following the nearest enemy (he says the civilian to stop following him) and tries to hunt him down. If he manages to him down he returns to the civilian (unless he sees another alien nearby or unless the civilian dies).
8) The bot targets the alien that is the nearest to the nearest civilian. If there is no civilian nearby or no civilian at all, he targets the one closest to him. Keeping distance from aliens the bot is targetting is NOT implemented.