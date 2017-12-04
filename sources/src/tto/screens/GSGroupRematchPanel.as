package tto.screens {
	import feathers.controls.Button;
	import feathers.controls.Header;
	import starling.display.DisplayObject;
	import starling.events.Event;
	import tto.datas.Save;
	import tto.Game;
	import tto.utils.i18n;
	import tto.utils.SoundManager;
	/**
	 * ...
	 * @author Mao
	 */
	public class GSGroupRematchPanel extends RematchPanel {
		
		public function GSGroupRematchPanel(params:Object) {
			super(params);
		}
		
		override protected function rematchFooter():Header {
			var footer:Header = new Header();
			exitBtn = new Button();
			exitBtn.label = i18n.gettext('STR_QUIT');
			exitBtn.addEventListener(Event.TRIGGERED, exitBtnHandler);
			footer.leftItems = new <DisplayObject>[exitBtn];
			if (_params.NEXT_STEP < 6) {
				rematchBtn = new Button();
				rematchBtn.label = i18n.gettext('STR_NEXT_MATCH');
				rematchBtn.addEventListener(Event.TRIGGERED, nextLesson);
				footer.rightItems = new <DisplayObject>[rematchBtn];
			}
			return footer;
		}
		
		override protected function exitBtnHandler(e:Event):void {
			SoundManager.fadeSoundChannel(SoundManager._playingChannel, 75, 0);
			this.dispatchEventWith('gotoScreen', true, 'PVE_SCREEN');
		}
		
		private function nextLesson(e:Event):void {
			Game.PROFILE_DATAS.STARTED_MATCHES += 1;
			Game.PROFILE_DATAS.PVE_MATCHES += 1;
			Save.save(Game.PROFILE_DATAS);
			this.dispatchEventWith('gs_next_match', true, { STEP:uint(_params.NEXT_STEP) });
		}
	}

}