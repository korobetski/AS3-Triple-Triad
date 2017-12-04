package tto.utils {
	import flash.display.MovieClip;
	import flash.events.Event;
	import flash.media.Sound;
	import flash.media.SoundChannel;
	import flash.media.SoundMixer;
	import flash.media.SoundTransform;
	import flash.utils.setInterval;
	import flash.utils.setTimeout;
	
	/**
	 * ...
	 * @author Mao
	 */
	public class SoundManager {
		private static var _TOOLBOX_:MovieClip = new MovieClip();
		public static var BACKGROUND_CHANNEL:SoundChannel = new SoundChannel();
		public static var NOISE_CHANNEL:SoundChannel = new SoundChannel();
		
		public static var BACKGROUND_VOLUME:Number = 1;
		public static var NOISE_VOLUME:Number = 1;
		public static var SOUND_MIXER:SoundMixer = new SoundMixer();
		
		public static var fadeInterval:uint;
		public static var _isPlaying:Boolean = false;
		public static var _playingSound:Sound;
		public static var _playingChannel:SoundChannel;
		
		public function SoundManager() {
		
		}
		
		public static function playSound(soundId:String, isNoise:Boolean = false, loops:uint = 0):void {
			var _tempSoundChannel:SoundChannel;
			BACKGROUND_VOLUME = conf.DATAS.background_volume;
			NOISE_VOLUME = conf.DATAS.noise_volume;
			var ST:SoundTransform = new SoundTransform(1, 0);
			var sound:Sound = Assets.manager.getSound(soundId);
			if (isNoise) {
				ST.volume = NOISE_VOLUME;
				_tempSoundChannel = sound.play(0, loops, ST);
			} else {
				if (!SoundManager._isPlaying) {
					ST.volume = BACKGROUND_VOLUME;
					SoundMixer.stopAll();
					_tempSoundChannel = sound.play(0, loops, ST);
					SoundManager._isPlaying = true;
					SoundManager._playingSound = sound;
					SoundManager._playingChannel = _tempSoundChannel;
				} else {
					if (sound !== SoundManager._playingSound) {
						SoundManager.fadeSoundChannel(SoundManager._playingChannel, 150, 0, function():void {
								SoundManager.stop();
								ST.volume = BACKGROUND_VOLUME;
								_tempSoundChannel = sound.play(0, loops, ST);
								SoundManager._isPlaying = true;
								SoundManager._playingSound = sound;
								SoundManager._playingChannel = _tempSoundChannel;
							});
					}
				}
			}
		}
		
		public static function shuffleLoop():void {
			if (!SoundManager._isPlaying) {
				var shuffleChannel:SoundChannel = new SoundChannel();
				var shuffle:Sound = Assets.manager.getSound('shuffle_or_boogie');
				var ST:SoundTransform = new SoundTransform(conf.DATAS.background_volume, 0);
				shuffleChannel = shuffle.play(0, 0, ST);
				SoundManager._isPlaying = true;
				SoundManager._playingSound = shuffle;
				SoundManager._playingChannel = shuffleChannel;
				SoundManager._TOOLBOX_.addEventListener(Event.ENTER_FRAME, progressHandler)
			}
		
		}
		
		private static function progressHandler(e:Event):void {
			if (SoundManager._playingChannel.position > 64300) {
				var ST:SoundTransform = new SoundTransform(conf.DATAS.background_volume, 0);
				SoundManager._playingChannel = SoundManager._playingSound.play(16374.00, 0, ST);
			}
		}
		
		public static function stop():void {
			var channel:SoundChannel = SoundManager._playingChannel;
			if (channel)
				channel.stop();
			SoundManager._isPlaying = false;
			//SoundMixer.stopAll();
			
			SoundManager._playingSound = null;
			SoundManager._playingChannel = null;
			
			if (SoundManager._TOOLBOX_.hasEventListener(Event.ENTER_FRAME))
				SoundManager._TOOLBOX_.removeEventListener(Event.ENTER_FRAME, progressHandler)
		}
		
		public static function fadeSoundChannel(channel:SoundChannel, delay:int, new_volume:Number, onComplete:Function = null):void {
			if (channel) {
				var transform:SoundTransform = channel.soundTransform;
				if (transform.volume < new_volume) {
					transform.volume += 0.01;
					channel.soundTransform = transform;
					fadeInterval = setInterval(fadeSoundChannel, delay, channel, delay, new_volume, onComplete);
				} else if (transform.volume > new_volume) {
					transform.volume -= 0.01;
					channel.soundTransform = transform;
					setTimeout(fadeSoundChannel, delay, channel, delay, new_volume, onComplete);
				}
				if (transform.volume == 0) {
					SoundManager.stop();
					if (onComplete !== null)
						onComplete();
				}
			} else {
				SoundManager.stop();
			}
		}
		
		public static function setChannelVolume(channel:SoundChannel, volume:Number):void {
			var ST:SoundTransform = channel.soundTransform;
			ST.volume = BACKGROUND_VOLUME;
			channel.soundTransform = ST;
		}
		
		static public function get isPlaying():Boolean {
			return SoundManager._isPlaying;
		}
	}

}