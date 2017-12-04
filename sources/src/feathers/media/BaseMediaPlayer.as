/*
Feathers
Copyright 2012-2015 Joshua Tynjala. All Rights Reserved.

This program is free software. You can redistribute and/or modify it in
accordance with the terms of the accompanying license agreement.
*/
package feathers.media
{
	import feathers.controls.LayoutGroup;
	import feathers.layout.AnchorLayout;

	import starling.display.DisplayObject;
	import starling.display.DisplayObjectContainer;

	import starling.events.Event;

	public class BaseMediaPlayer extends LayoutGroup implements IMediaPlayer
	{
		public function BaseMediaPlayer()
		{
			super();
			this.addEventListener(Event.ADDED, mediaPlayer_addedHandler);
			this.addEventListener(Event.REMOVED, mediaPlayer_removedHandler);
		}

		/**
		 * @private
		 */
		override protected function initialize():void
		{
			if(!this._layout)
			{
				this.layout = new AnchorLayout();
			}
			super.initialize();
		}

		/**
		 * @private
		 */
		protected function handleAddedChild(child:DisplayObject):void
		{
			if(child is IMediaPlayerControl)
			{
				IMediaPlayerControl(child).mediaPlayer = this;
			}
			if(child is DisplayObjectContainer)
			{
				var container:DisplayObjectContainer = DisplayObjectContainer(child);
				var childCount:int = container.numChildren;
				for(var i:int = 0; i < childCount; i++)
				{
					child = container.getChildAt(i);
					this.handleAddedChild(child);
				}
			}
		}

		/**
		 * @private
		 */
		protected function handleRemovedChild(child:DisplayObject):void
		{
			if(child is IMediaPlayerControl)
			{
				IMediaPlayerControl(child).mediaPlayer = null;
			}
			if(child is DisplayObjectContainer)
			{
				var container:DisplayObjectContainer = DisplayObjectContainer(child);
				var childCount:int = container.numChildren;
				for(var i:int = 0; i < childCount; i++)
				{
					child = container.getChildAt(i);
					this.handleRemovedChild(child);
				}
			}
		}

		/**
		 * @private
		 */
		protected function mediaPlayer_addedHandler(event:Event):void
		{
			var addedChild:DisplayObject = DisplayObject(event.target);
			this.handleAddedChild(addedChild);
		}

		/**
		 * @private
		 */
		protected function mediaPlayer_removedHandler(event:Event):void
		{
			var removedChild:DisplayObject = DisplayObject(event.target);
			this.handleRemovedChild(removedChild);
		}
	}
}
