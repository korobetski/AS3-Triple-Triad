package tto.datas {
	import tto.utils.i18n;
	
	/**
	 * ...
	 * @author Mao
	 */
	public class CardItem extends Item {
		private var _cardId:uint;
		private var _datas:Object;
		
		public function CardItem(cardId:uint) {
			super();
			
			this.type = Item.ITEM_TYPE_CARD;
			
			_cardId = cardId;
			_datas = cards.DATAS[_cardId];
			if (i18n.language == "fr_FR")
				name = i18n.gettext('STR_CARD') + ' ' + i18n.gettext(_datas.name);
			else
				name = i18n.gettext(_datas.name) + ' ' + i18n.gettext('STR_CARD');
			this.iconId = 'card_r' + String(_datas.rarity) + '_icon';
			description = i18n.gettext('STR_CARD_ITEM_DESC');
			value = _cardId * 4;
			
			sellable = true;
			stackable = true;
			useable = true;
			dropable = true;
		}
		
		override public function __toJSON():Object {
			var _JSON:Object = {type: Item.ITEM_TYPE_CARD, card: _cardId, stack: this.stack};
			return _JSON;
		}
		
		public function get cardId():uint {
			return _cardId;
		}
		
		public function set cardId(value:uint):void {
			_cardId = value;
			super.iconId = 'card_r' + String(_datas.rarity) + '_icon';
		}
	
	}

}