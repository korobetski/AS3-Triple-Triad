package tto.anims {
	import feathers.controls.Label;
	import flash.utils.setTimeout;
	import starling.core.Starling;
	import starling.display.Image;
	import starling.display.Sprite;
	import starling.events.Event;
	import starling.text.TextField;
	import starling.text.TextFieldAutoSize;
	import tto.utils.Assets;
	import tto.utils.FilterProvider;
	
	/**
	 * ...
	 * @author Mao
	 */
	public class TalkAnim extends Sprite {
		private var gfx:Image;
		private var _npcName:String;
		private var _message:String;
		private var messageTF:TextField;
		private var npcNameTF:TextField;
		
		public function TalkAnim(message:String, npcName:String) {
			super();
			_npcName = npcName;
			_message = message;
			
			addEventListener(Event.ADDED_TO_STAGE, init);
		}
		
		private function init(e:Event):void {
			removeEventListener(Event.ADDED_TO_STAGE, init);
			
			this.touchable = false;
			this.width = stage.width;
			this.height = stage.height;
			
			gfx = new Image(Assets.manager.getTexture('talk_basic.tex'));
			gfx.pivotX = gfx.width / 2;
			gfx.pivotY = gfx.height / 2;
			this.addChild(gfx);
			gfx.x = stage.width / 2;
			gfx.y = stage.height / 6;
			gfx.scaleX = 1.5
			gfx.scaleY = 1.5;
			gfx.alpha = 0;
			
			Starling.juggler.tween(gfx, 0.4, {scaleX:1, scaleY:1, alpha:1, onComplete:predispose } );
		}
		
		private function predispose():void {
			npcNameTF = new TextField(450, 26, _npcName, 'Raleway', 14, 0xffffff, true);
			npcNameTF.autoSize = TextFieldAutoSize.VERTICAL;
			npcNameTF.nativeFilters = [FilterProvider.blackBorder, FilterProvider.blackDropShadow]
			this.addChild(npcNameTF);
			npcNameTF.x = stage.width / 9;
			npcNameTF.y = stage.height / 10-18;
			
			messageTF = new TextField(450, 75, _message, 'Raleway', 16, 0x202020, true);
			messageTF.autoSize = TextFieldAutoSize.VERTICAL;
			messageTF.pivotX = messageTF.width / 2;
			messageTF.pivotY = messageTF.height / 2;
			this.addChild(messageTF);
			messageTF.x = stage.width / 2;
			messageTF.y = stage.height / 6;
			setTimeout(function():void { npcNameTF.visible = messageTF.visible = false }, 5000);
			Starling.juggler.tween(gfx, 0.4, { delay:5, scaleX:0.5, scaleY:0.5, alpha:0, onComplete:dispose } );
		}
		
		override public function dispose():void {
			this.removeChild(gfx);
			this.removeChild(messageTF);
			super.dispose();
		}
		
		public function get npcName():String 
		{
			return _npcName;
		}
		
		public function set npcName(value:String):void 
		{
			_npcName = value;
		}
	}

}