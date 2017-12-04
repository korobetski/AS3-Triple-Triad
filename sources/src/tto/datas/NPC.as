package tto.datas {
	import com.adobe.utils.ArrayUtil;
	import starling.display.Image;
	import tto.utils.Assets;
	import tto.utils.i18n;
	import tto.utils.tools;
	
	/**
	 * ...
	 * @author Mao
	 */
	public class NPC extends Object {
		public static const LEVEL_NONE:String = 'STR_NPC_LEVEL_NONE';
		public static const LEVEL_NOVICE:String = 'STR_NPC_LEVEL_NOVICE';
		public static const LEVEL_INITIATE:String = 'STR_NPC_LEVEL_INITIATE';
		public static const LEVEL_AVERAGE:String = 'STR_NPC_LEVEL_AVERAGE';
		public static const LEVEL_ADVANCED:String = 'STR_NPC_LEVEL_ADVANCED';
		public static const LEVEL_EXPERT:String = 'STR_NPC_LEVEL_EXPERT';
		public static const LEVELS_MODIFIER:Object = {STR_NPC_LEVEL_NONE: 0, STR_NPC_LEVEL_NOVICE: 1, STR_NPC_LEVEL_INITIATE: 2, STR_NPC_LEVEL_AVERAGE: 3, STR_NPC_LEVEL_ADVANCED: 4, STR_NPC_LEVEL_EXPERT: 5};
		
		private var _id:uint = 0;
		private var _name:String = 'NPC';
		private var _iconID:String = 'NPC_ICON';
		private var _icon:Image;
		private var _rules:Array = [];
		private var _cards:Array = [];
		private var _fetishesCards:Array = [];
		private var _level:String = NPC.LEVEL_NONE;
		private var _matchFee:uint = 0;
		private var _MGPReward:Object = {win: 0, draw: 0, lose: 0};
		private var _XPReward:Object;
		private var _itemRewards:Array = [];
		private var _difficulty:uint = 0;
		private var _availability:Object = {begins: 0, ends: 0};
		
		public function NPC(datas:Object) {
			_id = datas.id;
			_name = datas.name;
			_iconID = datas.iconID;
			if (Assets.manager.getTexture(_iconID) !== null) {
				_icon = new Image(Assets.manager.getTexture(_iconID));
				_icon.width = _icon.height = 50;
			}
			_rules = datas.rules;
			_cards = datas.cards;
			_fetishesCards = datas.fetishesCards;
			this.level = datas.level;
			_matchFee = datas.matchFee;
			_MGPReward = datas.MGPReward;
			_itemRewards = datas.itemRewards;
			_difficulty = datas.difficulty;
			_availability = datas.availability;
		}
		
		public function toListItemDatas():Object {
			return {label: i18n.gettext(_name), accessory: _icon, npc: this};
		}
		
		public function get name():String {
			return _name;
		}
		
		public function set name(value:String):void {
			_name = value;
		}
		
		public function get rules():Array {
			return _rules;
		}
		
		public function get gameRules():Object {
			var gameRules:Object = {OPEN_RULE: tripleTriadRules.RULE_DEFAULT_OPEN, SUDDEN_DEATH: false, RANDOM: false, ORDER: tripleTriadRules.RULE_DEFAULT_ORDER, REVERSE: false, FALLEN_ACE: false, SAME: false, SAME_WALL: false, PLUS: false, TYPE_RULE: tripleTriadRules.RULE_DEFAULT_TYPE, SWAP: false, ROULETTE: false};
			
			for each (var rule:String in _rules) {
				switch (rule) {
					case(tripleTriadRules.RULE_ALL_OPEN): 
						gameRules.OPEN_RULE = rule;
						break;
					case(tripleTriadRules.RULE_THREE_OPEN): 
						gameRules.OPEN_RULE = rule;
						break;
					case(tripleTriadRules.RULE_SUDDEN_DEATH): 
						gameRules.SUDDEN_DEATH = true;
						break;
					case(tripleTriadRules.RULE_RANDOM): 
						gameRules.RANDOM = true;
						break;
					case(tripleTriadRules.RULE_ORDER): 
						gameRules.ORDER = rule;
						break;
					case(tripleTriadRules.RULE_CHAOS): 
						gameRules.ORDER = rule;
						break;
					case(tripleTriadRules.RULE_REVERSE): 
						gameRules.REVERSE = true;
						break;
					case(tripleTriadRules.RULE_FALLEN_ACE): 
						gameRules.FALLEN_ACE = true;
						break;
					case(tripleTriadRules.RULE_SAME): 
						gameRules.SAME = true;
						break;
					case(tripleTriadRules.RULE_SAME_WALL): 
						gameRules.SAME_WALL = true;
						break;
					case(tripleTriadRules.RULE_PLUS): 
						gameRules.PLUS = true;
						break;
					case(tripleTriadRules.RULE_ASCENSION): 
						gameRules.TYPE_RULE = rule;
						break;
					case(tripleTriadRules.RULE_DESCENSION): 
						gameRules.TYPE_RULE = rule;
						break;
					case(tripleTriadRules.RULE_ELEMENTAL): 
						gameRules.TYPE_RULE = rule;
						break;
					case(tripleTriadRules.RULE_SWAP): 
						gameRules.SWAP = true;
						break;
					case(tripleTriadRules.RULE_ROULETTE): 
						gameRules.ROULETTE = true;
						break;
				}
			}
			return gameRules;
		}
		
		public function set rules(value:Array):void {
			_rules = value;
		}
		
		public function get fetishesCards():Array {
			return _fetishesCards;
		}
		
		public function set fetishesCards(value:Array):void {
			_fetishesCards = value;
		}
		
		public function get cards():Array {
			return _cards;
		}
		
		public function set cards(value:Array):void {
			_cards = value;
		}
		
		public function get matchFee():uint {
			return _matchFee;
		}
		
		public function set matchFee(value:uint):void {
			_matchFee = value;
		}
		
		public function get MGPReward():Object {
			return _MGPReward;
		}
		
		public function set MGPReward(value:Object):void {
			_MGPReward = value;
		}
		
		public function get itemRewards():Array {
			return _itemRewards;
		}
		
		public function set itemRewards(value:Array):void {
			_itemRewards = value;
		}
		
		public function get id():uint {
			return _id;
		}
		
		public function set id(value:uint):void {
			_id = value;
		}
		
		public function get iconID():String {
			return _iconID;
		}
		
		public function set iconID(value:String):void {
			_iconID = value;
			_icon.texture = Assets.manager.getTexture(_iconID);
		}
		
		public function get icon():Image {
			return _icon;
		}
		
		public function get XPReward():Object {
			if (_XPReward !== null)
				return _XPReward;
			else {
				if (_level !== NPC.LEVEL_NONE) {
					this.level = _level;
					return _XPReward;
				} else {
					this.level = NPC.LEVEL_NONE;
					return _XPReward;
				}
			}
		}
		
		public function get level():String {
			return _level;
		}
		
		public function set level(value:String):void {
			if (value == NPC.LEVEL_NOVICE || value == NPC.LEVEL_INITIATE || value == NPC.LEVEL_AVERAGE || value == NPC.LEVEL_ADVANCED || value == NPC.LEVEL_EXPERT) {
				_level = value;
				_XPReward = {w: 25 + Math.round(LEVELS_MODIFIER[_level] * 2), d: 10 + Math.round(LEVELS_MODIFIER[_level] * 1.5), l: 5 + Math.round(LEVELS_MODIFIER[_level])};
			} else {
				_level = NPC.LEVEL_NONE;
			}
		}
		
		public function get difficulty():uint {
			return _difficulty;
		}
		
		public function set difficulty(value:uint):void {
			_difficulty = value;
		}
		
		public function get availability():Object {
			return _availability;
		}
		
		public function set availability(value:Object):void {
			_availability = value;
		}
		
		public function getRewardItem():Item {
			var rewardItem:Object = this._itemRewards[tools.rand(this._itemRewards.length - 1)];
			var jet:Number = Math.random();
			if (rewardItem.card) {
				if (rewardItem.rate > jet) {
					return new CardItem(rewardItem.card);
				} else {
					return null;
				}
			} else if (rewardItem.potion) {
				if (rewardItem.rate > jet) {
					return new PotionItem(rewardItem.potion);
				} else {
					return null;
				}
			} else {
				return null;
			}
		}
		
		public function getRewardItems():Array {
			var rewards:Array = [];
			var jet:Number
			var rewardItem:Object
			for (var i:uint = 0, l:uint = this._itemRewards.length; i < l; i++) {
				rewardItem = this._itemRewards[i];
				jet = Math.random();
				trace('getRewardItems : type = '+rewardItem.type+' | '+rewardItem.rate+' <-> '+jet)
				if (rewardItem.rate > jet) {
					if (rewardItem.type == "card")
						rewards.push(new CardItem(rewardItem.card));
					if (rewardItem.type == "potion")
						rewards.push(new PotionItem(rewardItem.potion));
					if (rewardItem.type == "booster")
						rewards.push(new BoosterItem(rewardItem.booster));
				}
			}
			return rewards;
		}
		
		/**
		 * Randomly choose NPC cards
		 */
		public function getRandomCards():Array {
			var npcCards:Array = new Array();
			npcCards = ArrayUtil.copyArray(this._fetishesCards);
			if (npcCards.length < 5) {
				var randomizer:Array = ArrayUtil.copyArray(this._cards);
				var index:uint
				var randomCard:uint
				while (npcCards.length < 5) {
					index = Math.round(Math.random() * (randomizer.length - 1));
					randomCard = randomizer.splice(index, 1)[0];
					npcCards.push(randomCard);
					if (npcCards.length == 5)
						break;
				}
			}
			return npcCards;
		}
	
	}

}