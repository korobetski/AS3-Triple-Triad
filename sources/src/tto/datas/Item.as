package tto.datas {
	import feathers.controls.Label;
	import starling.display.Sprite;
	import starling.events.Event;
	import starling.events.Touch;
	import starling.events.TouchEvent;
	import starling.events.TouchPhase;
	import starling.textures.Texture;
	import tto.display.ItemIcon;
	
	/**
	 * ...
	 * @author Mao
	 */
	public class Item extends Sprite {
		public static const ITEM_TYPE_CARD:String = 'item-type-card';
		public static const ITEM_TYPE_BOOSTER:String = 'item-type-booster';
		public static const ITEM_TYPE_POTION:String = 'item-type-potion';
		public static const ITEM_TYPE_ACCESSORY:String = 'item-type-accessory';
		public static const ITEM_TYPE_MISC:String = 'item-type-misc';
		
		private var _bagIndex:uint;
		private var _icon:ItemIcon;
		private var _description:String;
		private var _type:String;
		private var _stack:uint;
		private var _sellable:Boolean;
		private var _stackable:Boolean;
		private var _useable:Boolean;
		private var _dropable:Boolean;
		private var _value:uint;
		
		public function Item() {
			super();
			
			_icon = new ItemIcon('booster_pack_icon');
			addChild(_icon);
			
			_description = '';
			_type = Item.ITEM_TYPE_MISC;
			_stack = 1;
			_value = 1;
			_sellable = true;
			_stackable = true;
			_useable = false;
			_dropable = true;
			
			addEventListener(TouchEvent.TOUCH, onTouch);
		}
		
		private function onTouch(e:TouchEvent):void {
			var touch:Touch = e.getTouch(this, TouchPhase.ENDED);
			if (touch) {
				dispatchEventWith(Event.TRIGGERED, true);
			}
		}
		
		public function __toJSON():Object {
			var _JSON:Object = {type: this.type, stack: this.stack};
			return _JSON;
		}
		
		override public function get name():String {
			return super.name;
		}
		
		override public function set name(value:String):void {
			super.name = value;
		}
		
		public function get icon():ItemIcon {
			return _icon;
		}
		
		public function set iconId(value:String):void {
			_icon.textureId = value;
		}
		
		public function get thumb():Texture {
			return _icon.texture;
		}
		
		public function get type():String {
			return _type;
		}
		
		public function set type(value:String):void {
			_type = value;
		}
		
		public function get stack():uint {
			return _stack;
		}
		
		public function set stack(value:uint):void {
			_stack = value;
		}
		
		public function get sellable():Boolean {
			return _sellable;
		}
		
		public function set sellable(value:Boolean):void {
			_sellable = value;
		}
		
		public function get stackable():Boolean {
			return _stackable;
		}
		
		public function set stackable(value:Boolean):void {
			_stackable = value;
		}
		
		public function get bagIndex():uint {
			return _bagIndex;
		}
		
		public function set bagIndex(value:uint):void {
			_bagIndex = value;
		}
		
		public function get description():String {
			return _description;
		}
		
		public function set description(value:String):void {
			_description = value;
		}
		
		public function get value():uint {
			return _value;
		}
		
		public function set value(value:uint):void {
			_value = value;
		}
		
		public function get useable():Boolean {
			return _useable;
		}
		
		public function set useable(value:Boolean):void {
			_useable = value;
		}
		
		public function get dropable():Boolean {
			return _dropable;
		}
		
		public function set dropable(value:Boolean):void {
			_dropable = value;
		}
		
		public function get accessory():Label {
			var stackLabel:Label = new Label();
			stackLabel.text = String(this._stack);
			return stackLabel;
		}
		
		public static function itemize(params:Object):Item {
			if (params.type == Item.ITEM_TYPE_CARD) {
				var cardItem:CardItem = new CardItem(uint(params.card));
				cardItem.stack = params.stack;
				return cardItem;
			} else if (params.type == Item.ITEM_TYPE_BOOSTER) {
				var boosterItem:BoosterItem = new BoosterItem(params.booster);
				boosterItem.stack = params.stack;
				return boosterItem;
			} else if (params.type == Item.ITEM_TYPE_POTION) {
				var potionItem:PotionItem = new PotionItem(params.potion);
				potionItem.stack = params.stack;
				return potionItem;
			} else {
				return new Item();
			}
		
		}
		
		override public function dispose():void {
			removeEventListener(TouchEvent.TOUCH, onTouch);
			super.dispose();
		}
	
	}

}