package tto.datas 
{
	import tto.utils.Assets;
	import tto.utils.i18n;
	/**
	 * ...
	 * @author Mao
	 */
	public class PotionItem extends Item 
	{
		public static const POTION_TYPE_SMALL_XP:String = 'SMALL_XP_BOOST';
		public static const POTION_TYPE_SMALL_MGP:String = 'SMALL_MGP_BOOST';
		public static const POTION_TYPE_XP:String = 'XP_BOOST';
		public static const POTION_TYPE_MGP:String = 'MGP_BOOST';
		public static const POTION_TYPE_BIG_XP:String = 'BIG_XP_BOOST';
		public static const POTION_TYPE_BIG_MGP:String = 'BIG_MGP_BOOST';
		
		private static const SMALL_XP_BOOST_MOD:Object = {type:'XP', value:2};
		private static const SMALL_MGP_BOOST_MOD:Object = {type:'MGP', value:2};
		private static const XP_BOOST_MOD:Object = {type:'XP', value:5};
		private static const MGP_BOOST_MOD:Object = {type:'MGP', value:5};
		private static const BIG_XP_BOOST_MOD:Object = {type:'XP', value:10};
		private static const BIG_MGP_BOOST_MOD:Object = {type:'MGP', value:10};
		
		private var _potionType:String;
		
		public function PotionItem(potionType:String) 
		{
			super();
			
			_potionType = potionType;
			
			this.type = Item.ITEM_TYPE_POTION;
			
			name = i18n.gettext('STR_'+_potionType);
			this.iconId = 'potionItem';
			description = i18n.gettext('STR_' + _potionType+'_DESC');
			value = 0;
			
			sellable = false;
			stackable = true;
			useable = true;
			dropable = false;
		}
		
		override public function __toJSON():Object {
			var _JSON:Object = { type:Item.ITEM_TYPE_POTION, potion:_potionType, stack:this.stack };
			return _JSON;
		}
		
		public function get potionType():String 
		{
			return _potionType;
		}
		public function get modifier():Object 
		{
			return PotionItem[_potionType+'_MOD'];
		}
		
	}

}