package tto.datas {
	import tto.utils.i18n;
	
	/**
	 * ...
	 * @author Mao
	 */
	public class BoosterItem extends Item {
		public static const BOOSTER_TYPE_BRONZE:String = 'BRONZE_BOOSTER';
		public static const BOOSTER_TYPE_SILVER:String = 'SILVER_BOOSTER';
		public static const BOOSTER_TYPE_GOLD:String = 'GOLD_BOOSTER';
		public static const BOOSTER_TYPE_MITHRIL:String = 'MITHRIL_BOOSTER';
		public static const BOOSTER_TYPE_PLATINUM:String = 'PLATINUM_BOOSTER';
		public static const BOOSTER_TYPE_BEAST:String = 'BEAST_BOOSTER';
		public static const BOOSTER_TYPE_PRIMAL:String = 'PRIMAL_BOOSTER';
		public static const BOOSTER_TYPE_SCION:String = 'SCION_BOOSTER';
		public static const BOOSTER_TYPE_GARLEAN:String = 'GARLEAN_BOOSTER';
		
		public static const BRONZE_BOOSTER_CARDS:Vector.<uint> = new <uint>[4, 5, 8, 12, 27, 38];
		public static const SILVER_BOOSTER_CARDS:Vector.<uint> = new <uint>[14, 15, 16, 17, 19, 50, 56, 57];
		public static const GOLD_BOOSTER_CARDS:Vector.<uint> = new <uint>[28, 29, 30, 34, 51, 58, 68, 76];
		public static const MITHRIL_BOOSTER_CARDS:Vector.<uint> = new <uint>[39,108,109,113,123,52,70,72,73];
		public static const PLATINUM_BOOSTER_CARDS:Vector.<uint> = new <uint>[51, 55, 57, 63, 69, 71, 77, 80];
		public static const BEAST_BOOSTER_CARDS:Vector.<uint> = new <uint>[14, 15, 16, 17, 18, 27, 35, 36, 37,82,83,117,128];
		public static const PRIMAL_BOOSTER_CARDS:Vector.<uint> = new <uint>[40, 41, 42, 43, 52, 53, 54, 55, 61,97,98,137];
		public static const SCION_BOOSTER_CARDS:Vector.<uint> = new <uint>[19, 122, 46, 48, 49, 50, 56, 59, 60, 138];
		public static const GARLEAN_BOOSTER_CARDS:Vector.<uint> = new <uint>[31, 32, 47, 51, 64,119];
		
		public static const BRONZE_BOOSTER_ICON:String = 'booster_pack_icon';
		public static const SILVER_BOOSTER_ICON:String = 'booster_pack_icon';
		public static const GOLD_BOOSTER_ICON:String = 'booster_pack_icon';
		public static const MITHRIL_BOOSTER_ICON:String = 'booster_pack_icon';
		public static const PLATINUM_BOOSTER_ICON:String = 'booster_pack_icon';
		public static const BEAST_BOOSTER_ICON:String = 'beast_booster';
		public static const PRIMAL_BOOSTER_ICON:String = 'primal_booster';
		public static const SCION_BOOSTER_ICON:String = 'scion_booster';
		public static const GARLEAN_BOOSTER_ICON:String = 'garlean_booster';
		
		private var _boosterType:String;
		private var _boosterCards:Vector.<uint>;
		
		public function BoosterItem(boosterType:String) {
			super();
			
			this.type = Item.ITEM_TYPE_BOOSTER;
			_boosterType = boosterType;
			_boosterCards = BoosterItem[_boosterType + '_CARDS'];
			
			name = i18n.gettext('STR_' + _boosterType);
			this.iconId = BoosterItem[_boosterType + '_ICON']
			description = i18n.gettext('STR_' + _boosterType + '_DESC');
			value = 0;
			
			sellable = false;
			stackable = true;
			useable = true;
			dropable = false;
		}
		
		public function open():uint {
			var regularRand:Number = Math.random() * uint(this._boosterCards.length - 1);
			var downgrade:Number = Math.random() * 1.25;
			var fin:uint = Math.min(Math.round(regularRand * downgrade), this._boosterCards.length - 1);
			return this._boosterCards[fin];
		}
		
		override public function __toJSON():Object {
			var _JSON:Object = {type: Item.ITEM_TYPE_BOOSTER, booster: _boosterType, stack: this.stack};
			return _JSON;
		}
		
		public function get boosterType():String {
			return _boosterType;
		}
	
	}

}