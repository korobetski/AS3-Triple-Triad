package tto.screens {
	import feathers.controls.Alert;
	import feathers.controls.GroupedList;
	import feathers.controls.Header;
	import feathers.controls.Label;
	import feathers.controls.Panel;
	import feathers.controls.PickerList;
	import feathers.controls.Screen;
	import feathers.controls.Slider;
	import feathers.controls.TextArea;
	import feathers.data.HierarchicalCollection;
	import feathers.data.ListCollection;
	import flash.desktop.NativeApplication;
	import flash.net.FileReference;
	import flash.net.URLRequest;
	import flash.system.Capabilities;
	import flash.system.System;
	import starling.display.Button;
	import starling.display.DisplayObject;
	import starling.events.Event;
	import tto.utils.Assets;
	import tto.utils.conf;
	import tto.utils.i18n;
	import tto.utils.SoundManager;
	import tto.utils.tools;
	import tto.utils.TTOFiles;
	
	/**
	 * ...
	 * @author Mao
	 */
	public class SettingsScreen extends Screen {
		private var languagePicker:PickerList;
		private var backButton:Button;
		private var saveBtn:feathers.controls.Button;
		private var panel:Panel;
		private var backgroundVolumeSlider:Slider;
		private var noiseVolumeSlider:Slider;
		
		private var appXml:XML;
		private var ns:Namespace;
		private var appId:String;
		private var appVersion:String;
		private var appName:String;
		private var lineVersion:String;
		private var capab:Panel;
		private var capabilitiesTA:TextArea;
		private var list:GroupedList;
		private var versionLabel:Label;
		
		public function SettingsScreen() {
			super();
		}
		
		override protected function draw():void {
			super.draw();
			
			panel.height = 800 - 96 - 48;
			
			capab.x = panel.x + panel.width + 8;
			capab.width = 1200 - capab.x - 8;
			capab.height = 800 - 96 - 48;
			capabilitiesTA.width = capab.width - 16;
		}
		
		override protected function initialize():void {
			super.initialize();
			
			appXml = NativeApplication.nativeApplication.applicationDescriptor;
			ns = appXml.namespace();
			appId = appXml.ns::id[0];
			appVersion = appXml.ns::versionNumber[0];
			appName = appXml.ns::filename[0];
			
			panel = new Panel();
			panel.title = i18n.gettext('STR_SETTINGS');
			panel.x = 8;
			panel.y = 96;
			//panel.width = stage.width / 2 - 18;
			panel.headerFactory = headerFactory;
			panel.footerFactory = footerFactory;
			addChild(panel);
			
			versionLabel = new Label();
			versionLabel.text = appVersion;
			
			languagePicker = new PickerList();
			var languageList:ListCollection = new ListCollection();
			var userLangIndex:uint = 0;
			for (var langCode:String in conf.supportedLanguages) {
				if (conf.DATAS.language == langCode)
					userLangIndex = languageList.length;
				languageList.push({label: conf.supportedLanguages[langCode], code: langCode});
			}
			languagePicker.dataProvider = languageList;
			languagePicker.selectedIndex = userLangIndex;
			languagePicker.addEventListener(Event.CHANGE, laguagePickerHandler);
			
			backgroundVolumeSlider = new Slider();
			backgroundVolumeSlider.minimum = 0;
			backgroundVolumeSlider.step = 0.02;
			backgroundVolumeSlider.maximum = 1;
			backgroundVolumeSlider.value = SoundManager.BACKGROUND_VOLUME;
			backgroundVolumeSlider.addEventListener(Event.CHANGE, backgroundVolumeSliderHandler);
			
			noiseVolumeSlider = new Slider();
			noiseVolumeSlider.minimum = 0;
			noiseVolumeSlider.step = 0.02;
			noiseVolumeSlider.maximum = 1;
			noiseVolumeSlider.value = SoundManager.NOISE_VOLUME;
			noiseVolumeSlider.addEventListener(Event.CHANGE, noiseVolumeSliderHandler);
			
			list = new GroupedList();
			list.isSelectable = false;
			list.dataProvider = new HierarchicalCollection([{header: i18n.gettext('STR_GENERAL_SETTINGS'), children: [{label: i18n.gettext('STR_VERSION'), accessory: versionLabel}, {label: i18n.gettext('STR_LANGUAGE'), accessory: languagePicker},]}, {header: i18n.gettext('STR_AUDIO_SETTINGS'), children: [{label: i18n.gettext('STR_BACKGROUND_VOLUME'), accessory: backgroundVolumeSlider}, {label: i18n.gettext('STR_NOISE_VOLUME'), accessory: noiseVolumeSlider},]},]);
			panel.addChild(list);
			
			capab = new Panel();
			capab.title = i18n.gettext('STR_CAPABILITIES');
			capab.y = 96;
			addChild(capab);
			
			capabilitiesTA = new TextArea();
			capabilitiesTA.isEditable = false;
			capab.addChild(capabilitiesTA);
			
			capabilitiesTA.text += "playerType : " + Capabilities.playerType + "\n";
			capabilitiesTA.text += "version : " + Capabilities.version + "\n";
			capabilitiesTA.text += "isDebugger : " + Capabilities.isDebugger + "\n";
			capabilitiesTA.text += "manufacturer : " + Capabilities.manufacturer + "\n";
			capabilitiesTA.text += "os : " + Capabilities.os + "\n";
			capabilitiesTA.text += "language : " + Capabilities.language + "\n";
			capabilitiesTA.text += "pixelAspectRatio : " + Capabilities.pixelAspectRatio + "\n";
			capabilitiesTA.text += "screenDPI : " + Capabilities.screenDPI + "\n";
			capabilitiesTA.text += "screenResolutionX : " + Capabilities.screenResolutionX + "\n";
			capabilitiesTA.text += "screenResolutionY : " + Capabilities.screenResolutionY + "\n";
		}
		
		private function noiseVolumeSliderHandler(e:Event):void {
			conf.DATAS.noise_volume = noiseVolumeSlider.value;
			SoundManager.setChannelVolume(SoundManager.NOISE_CHANNEL, noiseVolumeSlider.value);
		}
		
		private function backgroundVolumeSliderHandler(e:Event):void {
			conf.DATAS.background_volume = backgroundVolumeSlider.value;
			SoundManager.setChannelVolume(SoundManager.BACKGROUND_CHANNEL, backgroundVolumeSlider.value);
		}
		
		private function laguagePickerHandler(e:Event):void {
			//conf.DATAS.language = languagePicker.selectedItem.code;
		}
		
		private function headerFactory():Header {
			var header:Header = new Header();
			backButton = new Button(Assets.manager.getTexture('action_back_up'), '', Assets.manager.getTexture('action_back_down'));
			backButton.addEventListener(Event.TRIGGERED, backButton_triggeredHandler);
			
			header.leftItems = new <DisplayObject>[backButton];
			return header;
		}
		
		private function footerFactory():Header {
			var footer:Header = new Header();
			saveBtn = new feathers.controls.Button();
			saveBtn.label = i18n.gettext('STR_SAVE');
			saveBtn.addEventListener(Event.TRIGGERED, saveBtnHandler);
			
			// on récupère le n° de version de la dernière version sur le serveur.
			/*
			tools.fileOpen({url: 'http://www.triple-triad-online.com/update/application.xml', onComplete: function compare(e:*):void {
					var distXml:XML = XML(e.target.data);
					lineVersion = distXml.ns::versionNumber[0];
					
					if (Number(String(appVersion).replace('.', '')) < Number(String(lineVersion).replace('.', ''))) {
						var updateBtn:feathers.controls.Button = new feathers.controls.Button();
						updateBtn.label = i18n.gettext('STR_UPDATE_TO') + ' ' + String(lineVersion);
						updateBtn.addEventListener(Event.TRIGGERED, updateBtnHandler);
						footer.leftItems = new <DisplayObject>[updateBtn];
					}
					System.disposeXML(distXml);
				}});
			*/
			footer.rightItems = new <DisplayObject>[saveBtn];
			return footer;
		}
		
		private function updateBtnHandler(e:Event):void {
			var downloadURL:URLRequest = new URLRequest();
			var fileName:String = "TripleTriadOnline.";
			switch (Capabilities.manufacturer) {
				case('Adobe Windows'): 
					downloadURL.url = "http://www.triple-triad-online.com/update/tto.air";
					fileName += 'air';
					break;
				case('Android Linux'): 
					downloadURL.url = "http://www.triple-triad-online.com/update/tto.apk";
					fileName += 'apk';
					break;
				case('Adobe iOS'): 
					break;
			}
			
			var file:FileReference = new FileReference();
			file.download(downloadURL, fileName);
		}
		
		private function saveBtnHandler(e:Event):void {
			var newLanguage:String = languagePicker.selectedItem.code;
			if (newLanguage !== conf.DATAS.language) {
				conf.DATAS.language = newLanguage;
				Assets.manager.enqueue(TTOFiles.getFile('datas/locales/' + newLanguage + '.json', TTOFiles.APP_DIR));
				Assets.manager.enqueue(TTOFiles.getDirectory('assets/' + newLanguage + '/', TTOFiles.APP_DIR));
				Assets.manager.loadQueue(function(ratio:Number):void {
					if (ratio == 1) {
						i18n.loadLanguage(newLanguage, true);
						panel.title = i18n.gettext('STR_SETTINGS');
						list.dataProvider = new HierarchicalCollection([{header: i18n.gettext('STR_GENERAL_SETTINGS'), children: [{label: i18n.gettext('STR_VERSION'), accessory: versionLabel}, {label: i18n.gettext('STR_LANGUAGE'), accessory: languagePicker},]}, {header: i18n.gettext('STR_AUDIO_SETTINGS'), children: [{label: i18n.gettext('STR_BACKGROUND_VOLUME'), accessory: backgroundVolumeSlider}, {label: i18n.gettext('STR_NOISE_VOLUME'), accessory: noiseVolumeSlider}]}]);
						saveBtn.label = i18n.gettext('STR_SAVE');
						capab.title = i18n.gettext('STR_CAPABILITIES');
					}
				});
			}
			
			conf.save();
			
			Alert.show(i18n.gettext("STR_SETTINGS_SAVED"), i18n.gettext("STR_INFORMATION"), new ListCollection([{ label:i18n.gettext('STR_OK') }]));
			Assets.manager.enqueue(TTOFiles.getDirectory('assets/triad_rules/' + newLanguage + '/', TTOFiles.APP_DIR));
		}
		
		private function backButton_triggeredHandler(e:Event):void {
			dispatchEventWith('gotoScreen', false, 'MENU_SCREEN');
		}
		
		override public function dispose():void {
			languagePicker.removeEventListener(Event.CHANGE, laguagePickerHandler);
			backgroundVolumeSlider.removeEventListener(Event.CHANGE, backgroundVolumeSliderHandler);
			noiseVolumeSlider.removeEventListener(Event.CHANGE, noiseVolumeSliderHandler);
			saveBtn.removeEventListener(Event.TRIGGERED, saveBtnHandler);
			backButton.removeEventListener(Event.TRIGGERED, backButton_triggeredHandler);
			tools.purge(this);
			super.dispose();
		}
	}
}