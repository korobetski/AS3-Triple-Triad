/*
Feathers
Copyright 2012-2015 Joshua Tynjala. All Rights Reserved.

This program is free software. You can redistribute and/or modify it in
accordance with the terms of the accompanying license agreement.
*/
package feathers.media
{
	import feathers.controls.ImageLoader;
	import feathers.controls.LayoutGroup;
	import feathers.core.IFeathersControl;
	import feathers.core.PopUpManager;
	import feathers.events.FeathersEventType;

	import flash.display.Stage;
	import flash.display.StageDisplayState;
	import flash.events.IOErrorEvent;
	import flash.events.NetStatusEvent;
	import flash.media.SoundTransform;
	import flash.media.Video;
	import flash.net.NetConnection;
	import flash.net.NetStream;
	import flash.net.URLRequest;

	import starling.core.Starling;
	import starling.display.DisplayObject;
	import starling.events.Event;
	import starling.textures.Texture;

	public class VideoPlayer extends BaseTimedMediaPlayer implements IVideoPlayer
	{
		/**
		 * @private
		 */
		protected static const NET_STATUS_CODE_NETSTREAM_PLAY_STOP:String = "NetStream.Play.Stop";

		/**
		 * @private
		 */
		protected static const NET_STATUS_CODE_NETSTREAM_PLAY_STREAMNOTFOUND:String = "NetStream.Play.StreamNotFound";

		/**
		 * @private
		 */
		protected static const NET_STATUS_CODE_NETSTREAM_SEEK_NOTIFY:String = "NetStream.Seek.Notify";
		
		/**
		 * Constructor.
		 */
		public function VideoPlayer()
		{
		}

		/**
		 * @private
		 */
		protected var _fullScreenContainer:LayoutGroup;

		/**
		 * @private
		 */
		protected var _ignoreDisplayListEvents:Boolean = false;

		/**
		 * @private
		 */
		protected var _soundTransform:SoundTransform;

		public function get soundTransform():SoundTransform
		{
			if(!this._soundTransform)
			{
				this._soundTransform = new SoundTransform();
			}
			return this._soundTransform;
		}

		/**
		 * @private
		 */
		public function set soundTransform(value:SoundTransform):void
		{
			this._soundTransform = value;
			if(this._netStream)
			{
				this._netStream.soundTransform = this._soundTransform;
			}
		}

		/**
		 * @private
		 */
		protected var _texture:Texture;
		
		public function get texture():Texture
		{
			return this._texture;
		}
		
		public function get nativeWidth():Number
		{
			if(this._texture)
			{
				return this._texture.nativeWidth;
			}
			return 0;
		}

		public function get nativeHeight():Number
		{
			if(this._texture)
			{
				return this._texture.nativeHeight;
			}
			return 0;
		}

		/**
		 * @private
		 */
		protected var _netConnection:NetConnection;

		/**
		 * @private
		 */
		protected var _netStream:NetStream;

		/**
		 * The <code>flash.media.NetStream</code> object used to play the video.
		 */
		public function get netStream():NetStream
		{
			return this._netStream;
		}

		/**
		 * @private
		 */
		protected var _videoSource:String;

		public function get videoSource():String
		{
			return this._videoSource;
		}

		/**
		 * @private
		 */
		public function set videoSource(value:String):void
		{
			if(this._videoSource === value)
			{
				return;
			}
			this._videoSource = value;
			if(this._autoPlay)
			{
				this.play();
			}
		}

		/**
		 * @private
		 */
		protected var _autoPlay:Boolean = true;

		public function get autoPlay():Boolean
		{
			return this._autoPlay;
		}

		/**
		 * @private
		 */
		public function set autoPlay(value:Boolean):void
		{
			this._autoPlay = value;
		}

		/**
		 * @private
		 */
		protected var _isFullScreen:Boolean = false;

		/**
		 * Indicates if the video player is currently full screen or not.
		 */
		public function get isFullScreen():Boolean
		{
			return this._isFullScreen;
		}

		protected var _fullScreenDisplayState:String = StageDisplayState.FULL_SCREEN_INTERACTIVE;

		public function get fullScreenDisplayState():String
		{
			return this._fullScreenDisplayState;
		}

		public function set fullScreenDisplayState(value:String):void
		{
			if(this._fullScreenDisplayState == value)
			{
				return;
			}
			this._fullScreenDisplayState = value;
			if(this._isFullScreen)
			{
				var nativeStage:Stage = Starling.current.nativeStage;
				nativeStage.displayState = this._fullScreenDisplayState;
			}
		}
		
		override public function get hasVisibleArea():Boolean
		{
			if(this._isFullScreen)
			{
				return false;
			}
			return super.hasVisibleArea;
		}

		/**
		 * @private
		 */
		override public function dispose():void
		{
			if(this._texture)
			{
				this._texture.dispose();
				this._texture = null;
			}
			if(this._netStream)
			{
				this._netStream.close();
				this._netStream = null;
				this._netConnection = null;
			}
			super.dispose();
		}

		/**
		 * Goes to full screen or returns to normal display.
		 */
		public function toggleFullScreen():void
		{
			var nativeStage:Stage = Starling.current.nativeStage;
			var oldIgnoreDisplayListEvents:Boolean = this._ignoreDisplayListEvents;
			this._ignoreDisplayListEvents = true;
			if(this._isFullScreen)
			{
				PopUpManager.removePopUp(this._fullScreenContainer, false);
				var childCount:int = this._fullScreenContainer.numChildren;
				for(var i:int = 0; i < childCount; i++)
				{
					var child:DisplayObject = this._fullScreenContainer.getChildAt(0);
					this.addChild(child);
				}
				nativeStage.displayState = StageDisplayState.NORMAL;
			}
			else
			{
				nativeStage.displayState = this._fullScreenDisplayState;
				if(!this._fullScreenContainer)
				{
					this._fullScreenContainer = new LayoutGroup();
					this._fullScreenContainer.autoSizeMode = LayoutGroup.AUTO_SIZE_MODE_STAGE;
				}
				this._fullScreenContainer.layout = this._layout;
				childCount = this.numChildren;
				for(i = 0; i < childCount; i++)
				{
					child = this.getChildAt(0);
					this._fullScreenContainer.addChild(child);
				}
				PopUpManager.addPopUp(this._fullScreenContainer, true, false);
			}
			this._ignoreDisplayListEvents = oldIgnoreDisplayListEvents;
			this._isFullScreen = !this._isFullScreen;
			this.dispatchEventWith(MediaPlayerEventType.DISPLAY_STATE_CHANGE);
		}

		/**
		 * @private
		 */
		override protected function playMedia():void
		{
			if(!this._netStream)
			{
				this._netConnection = new NetConnection();
				this._netConnection.connect(null);
				this._netStream = new NetStream(this._netConnection);
				this._netStream.client = new VideoPlayerNetStreamClient(this.netStream_onMetaData);
				this._netStream.addEventListener(NetStatusEvent.NET_STATUS, netStream_netStatusHandler);
				this._netStream.addEventListener(IOErrorEvent.IO_ERROR, netStream_ioErrorHandler);
			}
			if(!this._soundTransform)
			{
				this._soundTransform = new SoundTransform();
			}
			this._netStream.soundTransform = this._soundTransform;
			if(this._texture)
			{
				this._netStream.resume();
			}
			else
			{
				this._netStream.play(this._videoSource);
			}
			if(!this._texture)
			{
				this._texture = Texture.fromNetStream(this._netStream, Starling.current.contentScaleFactor);
			}
			this.addEventListener(Event.ENTER_FRAME, videoPlayer_enterFrameHandler);
		}

		/**
		 * @private
		 */
		override protected function pauseMedia():void
		{
			this.removeEventListener(Event.ENTER_FRAME, videoPlayer_enterFrameHandler);
			this._netStream.pause();
		}

		/**
		 * @private
		 */
		override protected function seekMedia(seconds:Number):void
		{
			this._currentTime = seconds;
			this._netStream.seek(seconds);
		}

		/**
		 * @private
		 */
		protected function videoPlayer_enterFrameHandler(event:Event):void
		{
			this._currentTime = this._netStream.time;
			this.dispatchEventWith(MediaPlayerEventType.CURRENT_TIME_CHANGE);
		}

		/**
		 * @private
		 */
		protected function netStream_onMetaData(metadata:Object):void
		{
			this.dispatchEventWith(MediaPlayerEventType.DIMENSIONS_CHANGE);
			this._totalTime = metadata.duration;
			this.dispatchEventWith(MediaPlayerEventType.TOTAL_TIME_CHANGE);
		}

		/**
		 * @private
		 */
		protected function netStream_ioErrorHandler(event:IOErrorEvent):void
		{
			trace("video error", event);
		}

		/**
		 * @private
		 */
		protected function netStream_netStatusHandler(event:NetStatusEvent):void
		{
			var code:String = event.info.code;
			switch(code)
			{
				case NET_STATUS_CODE_NETSTREAM_PLAY_STREAMNOTFOUND:
				{
					this.dispatchEventWith(FeathersEventType.ERROR, false, code);
					break;
				}
				case NET_STATUS_CODE_NETSTREAM_PLAY_STOP:
				{
					if(this._isPlaying)
					{
						this.stop();
					}
					break;
				}
				case NET_STATUS_CODE_NETSTREAM_SEEK_NOTIFY:
				{
					this._currentTime = this._netStream.time;
					this.dispatchEventWith(MediaPlayerEventType.CURRENT_TIME_CHANGE);
					break;
				}
			}
		}

		/**
		 * @private
		 */
		override protected function mediaPlayer_addedHandler(event:Event):void
		{
			if(this._ignoreDisplayListEvents)
			{
				return;
			}
			super.mediaPlayer_addedHandler(event);
		}

		/**
		 * @private
		 */
		override protected function mediaPlayer_removedHandler(event:Event):void
		{
			if(this._ignoreDisplayListEvents)
			{
				return;
			}
			super.mediaPlayer_removedHandler(event);
		}
	}
}

dynamic class VideoPlayerNetStreamClient
{
	public function VideoPlayerNetStreamClient(onMetaDataCallback:Function)
	{
		this.onMetaDataCallback = onMetaDataCallback;
	}
	
	public var onMetaDataCallback:Function;
	
	public function onMetaData(metadata:Object):void
	{
		this.onMetaDataCallback(metadata);
	}
}