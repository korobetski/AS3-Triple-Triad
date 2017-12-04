package tto.datas 
{
	import com.adobe.utils.ArrayUtil;
	import starling.display.Image;
	import tto.Game;
	import tto.utils.Assets;
	import tto.utils.i18n;
	import tto.screens.InventoryScreen;
	
	/**
	 * ...
	 * @author Mao
	 */
	public class Achievements 
	{
		public static const list:Object = {
			"ac-tt1":{ label: 'STR_Triple_Team_I', iconID:'000713'},
			"ac-tt2":{ label: 'STR_Triple_Team_II', iconID:'000713'},
			"ac-tt3":{ label: 'STR_Triple_Team_III', iconID:'000713'},
			"ac-tt4":{ label: 'STR_Triple_Team_IV', iconID:'000713'},
			"ac-tt5":{ label: 'STR_Triple_Team_V', iconID:'000713'},
			"ac-wof1":{ label: 'STR_Wheel_Of_Fortune_I', iconID:'card_r1_icon'},
			"ac-wof2":{ label: 'STR_Wheel_Of_Fortune_II', iconID:'card_r2_icon'},
			"ac-wof3":{ label: 'STR_Wheel_Of_Fortune_III', iconID:'card_r2_icon'},
			"ac-wof4":{ label: 'STR_Wheel_Of_Fortune_IV', iconID:'card_r3_icon'},
			"ac-wof5":{ label: 'STR_Wheel_Of_Fortune_V', iconID:'card_r4_icon'},
			"ac-wof6":{ label: 'STR_Always_Bet_On_Me', iconID:'card_r5_icon'},
			"ac-td1":{ label: 'STR_Triple_decker_I', iconID:'card_r1_icon'},
			"ac-td2":{ label: 'STR_Triple_decker_II', iconID:'card_r2_icon'},
			"ac-td3":{ label: 'STR_Triple_decker_III', iconID:'card_r3_icon'},
			"ac-td4":{ label: 'STR_Triple_decker_IV', iconID:'card_r4_icon'},
			"ac-td5":{ label: 'STR_Triple_decker_V', iconID:'card_r5_icon'},
			"ac-mp1":{ label: 'STR_MGP_POT_I', iconID:'card_r1_icon'},
			"ac-mp2":{ label: 'STR_MGP_POT_II', iconID:'card_r2_icon'},
			"ac-mp3":{ label: 'STR_MGP_POT_III', iconID:'card_r3_icon'},
			"ac-mp4":{ label: 'STR_MGP_POT_IV', iconID:'card_r4_icon'},
			"ac-mp5":{ label: 'STR_MGP_POT_V', iconID:'card_r5_icon'},
			"ac-fob":{ label: 'STR_FRIEND_OF_BEASTS', iconID:'ff14_thumb_37'}
		};
		
		private var LIST:Array;
		
		public function Achievements() 
		{
			LIST = [
				{ id:"ac-tt1", label: i18n.gettext('STR_Triple_Team_I'), iconID:'000713', condition:(Game.PROFILE_DATAS.NPC_W_TOTAL >= 1)},// defeat 1 NPC
				{ id:"ac-tt2", label: i18n.gettext('STR_Triple_Team_II'), iconID:'000713', condition:(Game.PROFILE_DATAS.NPC_W_TOTAL >= 30) },// defeat 30 NPC
				{ id:"ac-tt3", label: i18n.gettext('STR_Triple_Team_III'), iconID:'000713', condition:(Game.PROFILE_DATAS.NPC_W_TOTAL >= 300), item:new CardItem(75) },// defeat 300 NPC
				{ id:"ac-tt4", label: i18n.gettext('STR_Triple_Team_IV'), iconID:'000713', condition:(Game.PROFILE_DATAS.NPC_W_TOTAL >= 3000) },// defeat 3000 NPC
				{ id:"ac-tt5", label: i18n.gettext('STR_Triple_Team_V'), iconID:'000713', condition:(Game.PROFILE_DATAS.NPC_W_TOTAL >= 7777) },// defeat 7777 NPC
				
				{ id:"ac-wof1", label: i18n.gettext('STR_Wheel_Of_Fortune_I'), iconID:'card_r1_icon', condition:(Game.PROFILE_DATAS.RULES_W['RULE_ROULETTE'] >= 1)},// win 1 roulette matches
				{ id:"ac-wof2", label: i18n.gettext('STR_Wheel_Of_Fortune_II'), iconID:'card_r2_icon', condition:(Game.PROFILE_DATAS.RULES_W['RULE_ROULETTE'] >= 10)},// win 10 roulette matches
				{ id:"ac-wof3", label: i18n.gettext('STR_Wheel_Of_Fortune_III'), iconID:'card_r2_icon', condition:(Game.PROFILE_DATAS.RULES_W['RULE_ROULETTE'] >= 30)},// win 30 roulette matches
				{ id:"ac-wof4", label: i18n.gettext('STR_Wheel_Of_Fortune_IV'), iconID:'card_r3_icon', condition:(Game.PROFILE_DATAS.RULES_W['RULE_ROULETTE'] >= 100)},// win 100 roulette matches
				{ id:"ac-wof5", label: i18n.gettext('STR_Wheel_Of_Fortune_V'), iconID:'card_r4_icon', condition:(Game.PROFILE_DATAS.RULES_W['RULE_ROULETTE'] >= 300), item:new CardItem(79)},// win 300 roulette matches
				{ id:"ac-wof6", label: i18n.gettext('STR_Always_Bet_On_Me'), iconID:'card_r5_icon', condition:(Game.PROFILE_DATAS.RULES_W['RULE_ROULETTE'] >= 1000)},// win 1000 roulette matches
				
				{ id:"ac-td1", label: i18n.gettext('STR_Triple_decker_I'), iconID:'card_r1_icon', condition:(Game.PROFILE_DATAS.CARDS.length >= 10)},// collect 10 cards
				{ id:"ac-td2", label: i18n.gettext('STR_Triple_decker_II'), iconID:'card_r2_icon', condition:(Game.PROFILE_DATAS.CARDS.length >= 30)},// collect 30 cards
				{ id:"ac-td3", label: i18n.gettext('STR_Triple_decker_III'), iconID:'card_r3_icon', condition:(Game.PROFILE_DATAS.CARDS.length >= 60)},// collect 60 cards
				{ id:"ac-td4", label: i18n.gettext('STR_Triple_decker_IV'), iconID:'card_r4_icon', condition:(Game.PROFILE_DATAS.CARDS.length >= 80)},// collect 80 cards
				{ id:"ac-td5", label: i18n.gettext('STR_Triple_decker_V'), iconID:'card_r5_icon', condition:(Game.PROFILE_DATAS.CARDS.length >= 110)},// collect 110 cards
				
				{ id:"ac-mp1", label: i18n.gettext('STR_MGP_POT_I'), iconID:'card_r1_icon', condition:(Game.PROFILE_DATAS.MGP >= 1000) },// collect 1000 MGP
				{ id:"ac-mp2", label: i18n.gettext('STR_MGP_POT_II'), iconID:'card_r2_icon', condition:(Game.PROFILE_DATAS.MGP >= 10000) },// collect 10.000 MGP
				{ id:"ac-mp3", label: i18n.gettext('STR_MGP_POT_III'), iconID:'card_r3_icon', condition:(Game.PROFILE_DATAS.MGP >= 100000) },// collect 100.000 MGP
				{ id:"ac-mp4", label: i18n.gettext('STR_MGP_POT_IV'), iconID:'card_r4_icon', condition:(Game.PROFILE_DATAS.MGP >= 400000) },// collect 400.000 MGP
				{ id:"ac-mp5", label: i18n.gettext('STR_MGP_POT_V'), iconID:'card_r5_icon', condition:(Game.PROFILE_DATAS.MGP >= 1000000) },// collect 1.000.000 MGP
				
				{ id:"ac-fob", label: i18n.gettext('STR_FRIEND_OF_BEASTS'), iconID:'ff14_thumb_37', condition:(Game.PROFILE_DATAS.MODE == 'ff14_' && ArrayUtil.arrayContainsValues(Game.PROFILE_DATAS.CARDS, [14, 15, 16, 17, 18, 27, 35, 36, 37,82,83,117,128])) },// have all beasts cards [14, 15, 16, 17, 18, 27, 35, 36, 37,82,83,117,128]
			]
		}
		
		public function check():Array {
			var success:Array = [];
			for each(var achievement:Object in LIST) {
				if (achievement.condition == true && !Game.PROFILE_DATAS.ACHIEVEMENTS[achievement.id]) {
					Game.PROFILE_DATAS.ACHIEVEMENTS[achievement.id] = new Date().getTime();
					achievement.icon = new Image(Assets.manager.getTexture(achievement.iconID));
					if (achievement.item !== undefined && achievement.item !== null) {
						var item:Item = achievement.item;
						Game.PROFILE_DATAS.BAG.push(item.__toJSON());
						InventoryScreen.sortBag();
					}
					success.push(achievement);
				}
			}
			
			return success;
		}
	}

}