package tto {
	import flash.desktop.NativeApplication;
	import flash.desktop.SystemIdleMode;
	import flash.desktop.Updater;
	import flash.display.DisplayObject;
	import flash.display.GradientType;
	import flash.display.Loader;
	import flash.display.MovieClip;
	import flash.display.NativeWindow;
	import flash.display.NativeWindowInitOptions;
	import flash.display.NativeWindowRenderMode;
	import flash.display.NativeWindowSystemChrome;
	import flash.display.NativeWindowType;
	import flash.display.Sprite;
	import flash.display.StageAlign;
	import flash.display.StageScaleMode;
	import flash.events.Event;
	import flash.events.KeyboardEvent;
	import flash.events.ProgressEvent;
	import flash.filesystem.File;
	import flash.geom.Matrix;
	import flash.net.URLRequest;
	import flash.system.Capabilities;
	import flash.system.System;
	import flash.text.TextField;
	import flash.text.TextFormat;
	import flash.ui.Keyboard;
	import tto.utils.gfx;
	import tto.utils.tools;
	import tto.utils.TTOFiles;
	
	/**
	 * ...
	 * @author Mao
	 */
	public class ttoboot extends Sprite {
		private var loader:Loader;
		private var _ttoupdater:DisplayObject;
		private var _ttoclient:DisplayObject;
		private var infoTF:TextField;
		private var appXml:XML;
		private var ns:Namespace;
		private var appId:String;
		private var appVersion:String;
		private var appName:String;
		private var lineVersion:String;
		private var newWindow:NativeWindow;
		
		public function ttoboot() {
			this.addEventListener(Event.ADDED_TO_STAGE, init);
		}
		
		private function init(e:Event):void {
			removeEventListener(Event.ADDED_TO_STAGE, init);
			
			appXml = NativeApplication.nativeApplication.applicationDescriptor;
			ns = appXml.namespace();
			appId = appXml.ns::id[0];
			appVersion = appXml.ns::versionNumber[0];
			appName = appXml.ns::filename[0];
			
			if (this.stage) {
				this.stage.scaleMode = StageScaleMode.NO_SCALE;
				this.stage.align = StageAlign.TOP_LEFT;
				
				if ((NativeApplication.nativeApplication.openedWindows.length > 0) && Capabilities.screenResolutionX > stage.width) {
					NativeApplication.nativeApplication.openedWindows[0].x = (Capabilities.screenResolutionX - NativeApplication.nativeApplication.openedWindows[0].width) / 2;
					NativeApplication.nativeApplication.openedWindows[0].y = (Capabilities.screenResolutionY - NativeApplication.nativeApplication.openedWindows[0].height) / 2;
				}
				if (Capabilities.cpuArchitecture == "ARM") {
					NativeApplication.nativeApplication.addEventListener(Event.ACTIVATE, handleActivate, false, 0, true);
					NativeApplication.nativeApplication.addEventListener(Event.DEACTIVATE, handleDeactivate, false, 0, true);
					NativeApplication.nativeApplication.addEventListener(KeyboardEvent.KEY_DOWN, handleKeys, false, 0, true);
				}
			}
			
			var mat:Matrix = new Matrix();
			mat.createGradientBox(400, 200);
			
			var background:Sprite = new Sprite();
			background.graphics.beginGradientFill(GradientType.LINEAR, [0x3481C0, 0x67C4E8], [1, 1],  [0, 255], mat);
			background.graphics.drawRoundRect(0, 0, 400, 200, 8, 8);
			background.graphics.endFill();
			background.x = 400;
			background.y = 300;
			//background.filters = [new DropShadowFilter(0, 45, 0x404040, 0.8, 5, 5, 1.0, 1)]
			addChild(background);
			
			var cubes:MovieClip = new MovieClip();
			cubes.x = 375;
			cubes.y = 300;
			addChild(cubes);
			gfx.trace_cube(cubes, 20, 100, 19, 0.75);
			
			var logo:MovieClip = new MovieClip();
			logo.x = 416;
			logo.y = 316;
			addChild(logo);
			tools.imageLoad(logo, TTOFiles.getFile('assets/logo_white_256.png', TTOFiles.APP_DIR).url)
			
			var mogs:MovieClip = new MovieClip();
			mogs.x = 650;
			mogs.y = 200;
			addChild(mogs);
			tools.imageLoad(mogs, TTOFiles.getFile('assets/mogs.png', TTOFiles.APP_DIR).url)
			
			loader = new Loader();
			var infoFormat:TextFormat = new TextFormat('_sans', 14, 0xffffff);
			
			infoTF = new TextField();
			infoTF.defaultTextFormat = infoFormat;
			infoTF.selectable = false;
			infoTF.width = 250;
			infoTF.x = 416;
			infoTF.y = 432;
			infoTF.text = 'Check for update';
			addChild(infoTF);
			
			var copyFormat:TextFormat = new TextFormat('_sans', 10, 0xffffff);
			var copyTF:TextField = new TextField();
			copyTF.defaultTextFormat = copyFormat;
			copyTF.selectable = false;
			copyTF.width = 250;
			copyTF.x = 620;
			copyTF.y = 480;
			copyTF.text = 'TTO '+appVersion+' © Moogle Works 2015';
			addChild(copyTF);
			
			if (!Capabilities.isDebugger && Updater.isSupported)
				airUpdate();
			else
				loadClient();
				
				this.addEventListener(Event.CLOSING, quit);
		}
		
		public function loadClient():void {
			var options:NativeWindowInitOptions = new NativeWindowInitOptions();
			options.systemChrome = NativeWindowSystemChrome.STANDARD;
			options.type = NativeWindowType.NORMAL;
			options.transparent = false;
			options.resizable = false;
			options.maximizable = false;
			options.renderMode = NativeWindowRenderMode.DIRECT;
			newWindow = new NativeWindow(options);
			newWindow.addEventListener(Event.CLOSING, quit);
			
			loader.load(new URLRequest(TTOFiles.getFile('ttoclient.swf', TTOFiles.APP_DIR).url));
			loader.contentLoaderInfo.addEventListener(Event.COMPLETE, displayClient);
		}
		
		private function displayClient(event:Event):void {
			NativeApplication.nativeApplication.openedWindows[0].close();
			//NativeApplication.nativeApplication.openedWindows[0] = null;
			delete NativeApplication.nativeApplication.openedWindows[0];
			
			loader.removeEventListener(Event.COMPLETE, displayClient);
			this.removeChildren();
			//_ttoclient = this.addChild(event.currentTarget.content);
			
			newWindow.stage.align = "TL";
			newWindow.stage.scaleMode = "noScale";
			newWindow.stage.addChild(event.target.content);
			newWindow.title = "Triple Triad Online " + appVersion;
			newWindow.x = (Capabilities.screenResolutionX - 1200) >> 1;
			newWindow.y = (Capabilities.screenResolutionY - 800) >> 1;
			
			newWindow.width = 1200;
			newWindow.height = 800;
			
			//activate and show the new window 
			newWindow.activate();
		}
		
		private function airUpdate():void {
			// on récupère le n° de version de la dernière version sur le serveur.
			tools.fileOpen({url: 'https://raw.githubusercontent.com/korobetski/tto/master/application.xml', onComplete: compare, onError: updateError});
			//loadClient();
		}
		
		private function compare(e:Event):void {
			var distXml:XML = XML(e.target.data);
			lineVersion = distXml.ns::versionNumber[0];
			infoTF.text = uintify(appVersion) + ' - ' + uintify(lineVersion);
			if (uintify(appVersion) < uintify(lineVersion)) {
				infoTF.text = 'Updating ...';
				TTOFiles.downloadAndWrite("raw.githubusercontent.com/korobetski/tto/master/tto.air", 'tto.air', TTOFiles.STORAGE_DIR, update, onProgress);
			} else {
				loadClient();
			}
			System.disposeXML(distXml);
		}
		
		private function onProgress(event:ProgressEvent):void {
			var loaded:Number = event.bytesLoaded;
			var total:Number = (event.bytesTotal > 0) ? event.bytesTotal : 7100107;
			var perc:int = Math.round(loaded * 100 / total);
			infoTF.text = 'Updating ' + perc + '%';
		}
		
		private function uintify(value:String):Number {
			return Number(value.replace('.', ''));
		}
		
		private function update():void {
			if (Updater.isSupported) {
				// on procède à l'installation de la mise à jour.;
				var updater:Updater = new Updater();
				var airFile:File = File.applicationStorageDirectory.resolvePath("tto.air");
				updater.update(airFile, lineVersion);
			} else {
				loadClient();
			}
		}
		
		private function updateError():void {
			// impossible de vérifier la mise à jour, accès internet peut-être indisponible
			// on charge néanmoins les paramètres locaux, on permet le jeu en solo
			loadClient();
		}
		
		private function handleActivate(event:Event):void {
			NativeApplication.nativeApplication.systemIdleMode = SystemIdleMode.KEEP_AWAKE;
		}
		
		private function handleDeactivate(event:Event):void {
			NativeApplication.nativeApplication.exit();
		}
		
		private function handleKeys(event:KeyboardEvent):void {
			if (event.keyCode == Keyboard.BACK)
				NativeApplication.nativeApplication.exit();
		}
		
		private function quit(e:Event):void {
			var exitingEvent:Event = new Event(Event.EXITING, false, true);
			NativeApplication.nativeApplication.dispatchEvent(exitingEvent);
			if (!exitingEvent.isDefaultPrevented()) {
				NativeApplication.nativeApplication.exit();
			} else {
				NativeApplication.nativeApplication.exit();
			}
		}
	}

}