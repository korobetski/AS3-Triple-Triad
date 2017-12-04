package tto.anims {
	import flash.display.MovieClip;
	import flash.display.Shape;
	import flash.events.Event;
	import tto.utils.tools;
	import tto.utils.TTOFiles;
	
	/**
	 * ...
	 * @author Mao
	 */
	public class Mogu extends MovieClip {
		private var _mask:Shape;
		private var _texture:MovieClip;
		private var _frame:uint;
		
		public function Mogu() {
			_texture = new MovieClip();
			addChild(_texture);
			
			tools.imageLoad(_texture, TTOFiles.getFile('assets/-mogu_anime_en.tex.png', TTOFiles.APP_DIR).url)
			
			_mask = new Shape();
			_mask.graphics.beginFill(0x000000, 1)
			_mask.graphics.drawRect(0, 0, 400, 168)
			addChild(_mask);
			_texture.mask = _mask
			
			_frame = 0;
		}
		
		override public function play():void {
			super.play();
			this.addEventListener(Event.ENTER_FRAME, texPosition);
		}
		
		override public function stop():void {
			super.stop();
			this.removeEventListener(Event.ENTER_FRAME, texPosition);
		}
		
		private function texPosition(e:Event):void {
			var frame:uint = Math.round(_frame / 2);
			var lane:int = frame % 4;
			_texture.x = -(lane * 400)
			_texture.y = -(((frame - lane) * 168) / 4);
			_frame++;
			if (_frame == 16 * 2) {
				_frame = 0;
				_texture.x = 0
				_texture.y = 0
			}
		}
	}

}