package tto.display 
{
	import feathers.controls.Callout;
	import starling.display.Sprite;
	import starling.display.Button;
	import starling.events.Event;
	import starling.events.Touch;
	import starling.events.TouchEvent;
	import starling.events.TouchPhase;
	import tto.datas.Item;
	
	/**
	 * ...
	 * @author Mao
	 */
	public class InventoryItem extends Button 
	{
		
		private var _item:Item;
		private var _callout:Callout;
		
		public function InventoryItem(__item:Item) 
		{
			_item = __item;
			super(_item.icon, String(_item.stack));
		}
		
		public function get item():Item 
		{
			return _item;
		}
		
		public function set item(value:Item):void 
		{
			_item = value;
		}
	}

}