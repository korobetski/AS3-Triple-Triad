package tto.controls 
{
	import starling.display.Image;
	import starling.display.Sprite;
	import tto.utils.Assets;
	
	/**
	 * ...
	 * @author Mao
	 */
	public class cardAvatar extends Sprite 
	{
		public static const BLUE_COLOR:String = 'BLUE';
		public static const RED_COLOR:String = 'RED';
		
		private var _color:String;
		private var _blueClip:Image;
		private var _redClip:Image;
		
		public function cardAvatar(card_color:String) 
		{
			super();
			
			_blueClip = new Image(Assets.manager.getTexture('m_blue'));
			addChild(_blueClip);
			_redClip = new Image(Assets.manager.getTexture('m_red'));
			addChild(_redClip);
			
			color = card_color;
		}
		
		public function set color(value:String):void {
			_color = value;
			_blueClip.visible = (_color == cardAvatar.BLUE_COLOR) ? true : false;
			_redClip.visible = (_color == cardAvatar.RED_COLOR) ? true : false;
		}
		
		public function get color():String {
			return _color;
		}
	}

}