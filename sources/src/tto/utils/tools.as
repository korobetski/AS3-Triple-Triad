package tto.utils {
	import com.adobe.utils.ArrayUtil;
	import flash.display.Bitmap;
	import flash.display.Loader;
	import flash.display.LoaderInfo;
	import flash.display.MovieClip;
	import flash.events.Event;
	import flash.events.IOErrorEvent;
	import flash.events.ProgressEvent;
	import flash.net.URLLoader;
	import flash.net.URLLoaderDataFormat;
	import flash.net.URLRequest;
	import flash.net.URLRequestMethod;
	import flash.net.URLVariables;
	import flash.system.LoaderContext;
	import flash.utils.getTimer;
	
	/**
	 * ...
	 * @author Mao
	 */

	public class tools {
		private static var __TOOLS_BOX__:* = new MovieClip();
		public function tools() {
			//  TOOLS  //------------------------------------------------------------------------------------------------------;
		}
		public static function fileOpen(params:Object):void {
			// fileOpen({url:String, variables:URLVariables, methode:String, format:String, onComplete:Function})
			// Fonction usuel pour appeller un fichier en lui soumettant des variables
			var url:String = (params.url) ? params.url : undefined,
			variables:URLVariables = (params.variables) ? params.variables : undefined,
			completeHandler:Function = (params.onComplete) ? params.onComplete : fileOpen_result,
			methode:String = (params.methode == 'POST') ? URLRequestMethod.POST : URLRequestMethod.GET,
			format:String = (params.format) ? params.format : URLLoaderDataFormat.TEXT,
			request:URLRequest = new URLRequest(url),
			static_loader:URLLoader = __TOOLS_BOX__['loader_' + getTimer()] = new URLLoader();

			if (variables) request.data = params.variables;
			request.method = methode;
			static_loader.dataFormat = format;
			static_loader.addEventListener(IOErrorEvent.IO_ERROR, fileOpen_error);
			static_loader.addEventListener(ProgressEvent.PROGRESS, fileOpen_prog);
			// si la function sortante n'est pas celle de base il faut faire disparaitre le waiter;
			if (completeHandler !== fileOpen_result) {
				static_loader.addEventListener(Event.COMPLETE, altHandler);
			} else {
				static_loader.addEventListener(Event.COMPLETE, completeHandler);
			}

			function fileOpen_error(e:IOErrorEvent):void {
				if (params.onError) {
					params.onError();
				}
			}
			function fileOpen_prog(event:ProgressEvent):void {
				var loaded:int = event.bytesLoaded, total:int = (event.bytesTotal > 0) ? event.bytesTotal : 150, perc:int = loaded / total;
			}
			function fileOpen_result(e:Event):void {
				static_loader = null;
			}
			function altHandler(e:Event):void {
				params.onComplete(e);
				static_loader = null;
			}

			try {
				static_loader.load(request);
			} catch (error:SecurityError) {
				trace('fileOpen error');
			}
		}
		
		public static function madmax(value:int):int {
			return Math.min(10, Math.max(0, value));
		}

		public static function rand(to:uint):uint {
			return Math.round(Math.random()*to);
		}
		
		public static function array_rand(arr:Array, num:uint = 1):* {
			var _arr:Array = ArrayUtil.copyArray(arr);
			if (num > _arr.length) {
				return null;
			} else {
				var r:Array = [];
				for (var i:uint = 0; i < num; i++) {
					r.push(_arr.splice(tools.rand(_arr.length-1), 1)[0]);
				}
				return num == 1 ? r[0] : r;
			}
		}
		
		public static function imageLoad(container:MovieClip, url:String, wh:uint = 0):void {
			var lc:LoaderContext = new LoaderContext(true);
        	lc.checkPolicyFile = true;
			var loader:Loader = __TOOLS_BOX__['loader_' + getTimer() + rand(9999)] = new Loader(), request:URLRequest = new URLRequest(url);

			purge(container);
			loader.contentLoaderInfo.addEventListener(IOErrorEvent.IO_ERROR, ioErrorHandler);
			loader.contentLoaderInfo.addEventListener(Event.INIT, load_init);
			loader.load(request, lc);
			function ioErrorHandler(evt:IOErrorEvent):void {
				
			}
			function load_init(evt:Event):void {
				var li:LoaderInfo = evt.target as LoaderInfo;
				try {
					var img:Bitmap = li.content as Bitmap, hov:Number = (img.height >= img.width) ? img.height : img.width, scale:Number = wh / hov;
					img.smoothing = true;
					if (wh > 0) {
						img.scaleX = scale;
						img.scaleY = scale;
					}
					container.addChild(img);
					li.removeEventListener(IOErrorEvent.IO_ERROR, ioErrorHandler);
					li.removeEventListener(Event.INIT, load_init);
				}
				catch(e:*) {
					// the redirected url
					var path:String = LoaderInfo(evt.target).url;
					var loader:Loader = new Loader();
					loader.contentLoaderInfo.addEventListener(Event.COMPLETE, onReallyComplete);
					// swap out the old loader with the new one into the same container
					LoaderInfo(evt.target).loader.parent.addChild(loader);
					LoaderInfo(evt.target).loader.parent.removeChild(LoaderInfo(evt.target).loader);
					var lc:LoaderContext = new LoaderContext(true);
					lc.checkPolicyFile = true;
					loader.load(new URLRequest(path), lc);
				}
			}
			
			function onReallyComplete(event:Event):void {
				var li:LoaderInfo = event.target as LoaderInfo;
				var img:Bitmap = li.content as Bitmap, hov:Number = (img.height >= img.width) ? img.height : img.width, scale:Number = wh / hov;
				if (wh > 0) {
					img.smoothing = true;
					img.scaleX = scale;
					img.scaleY = scale;
				}
				container.addChild(img);
				loader.contentLoaderInfo.removeEventListener(IOErrorEvent.IO_ERROR, ioErrorHandler);
				loader.contentLoaderInfo.removeEventListener(Event.INIT, load_init);
				loader.contentLoaderInfo.removeEventListener(Event.COMPLETE, onReallyComplete);
			}
		}

		public static function purge(container:*):void {
			// Fonction qui vide un conteneur.
			var nC:int = container.numChildren,i:int = 0;
			if (nC > 0) {
				for (i = 0; i<nC; i++) {
					container.removeChildAt(0);
				}
			}
		}
	}
}