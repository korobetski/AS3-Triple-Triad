package tto.screens {
	import feathers.controls.Button;
	import feathers.controls.Header;
	import starling.display.DisplayObject;
	import starling.events.Event;
	import tto.utils.i18n;
	import tto.utils.SoundManager;
	/**
	 * ...
	 * @author Mao
	 */
	public class TutorialRematchPanel extends RematchPanel {
		
		public function TutorialRematchPanel(params:Object) {
			super(params);
			
		}
		
		override protected function rematchFooter():Header {
			var footer:Header = new Header();
			rematchBtn = new Button();
			rematchBtn.label = i18n.gettext('STR_HELP');
			rematchBtn.addEventListener(Event.TRIGGERED, nextLesson);
			
			exitBtn = new Button();
			exitBtn.label = i18n.gettext('STR_QUIT');
			exitBtn.addEventListener(Event.TRIGGERED, exitBtnHandler);
			
			footer.leftItems = new <DisplayObject>[exitBtn];
			footer.rightItems = new <DisplayObject>[rematchBtn];
			
			return footer;
		}
		
		override protected function exitBtnHandler(e:Event):void {
			SoundManager.fadeSoundChannel(SoundManager._playingChannel, 75, 0);
			dispatchEventWith('gotoScreen', true, 'PVE_SCREEN');
		}
		
		private function nextLesson(e:Event):void {
			dispatchEventWith('gotoScreen', true, _params.NEXT_SCREEN);
		}
	}

}