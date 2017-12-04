package tto.utils {
	import flash.events.Event;
	import flash.events.ProgressEvent;
	import flash.filesystem.File;
	import flash.filesystem.FileMode;
	import flash.filesystem.FileStream;
	import flash.net.URLRequest;
	import flash.net.URLStream;
	import flash.utils.ByteArray;
	
	public class TTOFiles {
		public function TTOFiles() {
			//  TTOFiles  //------------------------------------------------------------------------------------------------------;
		}
		
		public static const STORAGE_DIR:String = 'Storage';
		public static const APP_DIR:String = 'Application';
		public static const DESK_DIR:String = 'Desktop';
		public static const DOC_DIR:String = 'Documents';
		public static const USER_DIR:String = 'User';
		
		
		public static function getFile(filePath:String, directory:String, create:Boolean = false):File {
			var file:File = fileDom(filePath, directory);
			if (!file.exists && create) createFile(filePath, directory);
			return file;
		}
		
		public static function createFile(filePath:String, directory:String):void {
			var file:File = fileDom(filePath, directory);
			if (!file.exists) {
				var fileStream:FileStream = new FileStream();
				fileStream.open(file, FileMode.WRITE);
				fileStream.writeUTFBytes('');
				fileStream.close();
			}
		}
		
		public static function renameFile(filePath:String, newName:String ,directory:String):void {
			var file:File = fileDom(filePath, directory);
			if (file.exists) {
				writeFile(newName, directory, readFile(file), true);
			}
		}
		public static function writeFile(filePath:String, directory:String, content:String, create:Boolean = false):void {
			var file:File = fileDom(filePath, directory);
			if ((!file.exists && create) || file.exists) {
				var fileStream:FileStream = new FileStream();
				fileStream.openAsync(file, FileMode.WRITE);
				fileStream.writeUTFBytes(content);
				fileStream.close();
			}
		}
		public static function append(filePath:String, directory:String, content:String):void {
			var file:File = fileDom(filePath, directory);
			if (!file.exists) createFile(filePath, directory);
			
			var fileStream:FileStream = new FileStream();
			fileStream.openAsync(file, FileMode.APPEND);
			fileStream.writeUTFBytes("\n"+content);
			fileStream.close();
		}
		public static function getDirectory(path:String, directory:String):Array {
			var file:File = fileDom(path, directory);
			if (file.isDirectory) {
				return file.getDirectoryListing();
			} else {
				return new Array();
			}
		}
		public static function readFile(file:File):String {
			var fileStream:FileStream = new FileStream();
			if (file.exists) {
				fileStream.open(file, FileMode.READ);
				return fileStream.readMultiByte(file.size,File.systemCharset);
				fileStream.close();
			} else {
				return '';
			}
		}
		public static function deleteFile(file:File):void {
			if (file.exists) {
				file.deleteFile();
			}
		}
		public static function downloadAndWrite(url:String, filePath:String, directory:String, whenLoaded:Function, onProgress:Function = null):void {
			var urlReq:URLRequest = new URLRequest(url);
			var urlStream:URLStream = new URLStream();
			var fileData:ByteArray = new ByteArray();
			urlStream.addEventListener(Event.COMPLETE, loaded);
			if (onProgress !== null) urlStream.addEventListener(ProgressEvent.PROGRESS, onProgress);
			urlStream.load(urlReq);
			function loaded(e:Event):void {
				urlStream.readBytes(fileData, 0, urlStream.bytesAvailable);
				var file:File = fileDom(filePath, directory);
				var fileStream:FileStream = new FileStream();
				fileStream.openAsync(file, FileMode.WRITE);
				fileStream.writeBytes(fileData, 0, fileData.length);
				fileStream.close();
				
				whenLoaded();
			}
		}
		public static function fileDom(filePath:String, directory:String):File {
			switch(directory) {
			case 'Storage':
				//a storage directory unique to each installed AIR application
				return File.applicationStorageDirectory.resolvePath(filePath);
				break;
			case 'Application':
				//the read-only directory where the application is installed (along with any installed assets)
				return File.applicationDirectory.resolvePath(filePath);
				break;
			case 'Desktop':
				//the user's desktop directory
				return File.desktopDirectory.resolvePath(filePath);
				break;
			case 'Documents':
				//the user's documents directory
				return File.documentsDirectory.resolvePath(filePath);
				break;
			case 'User':
				//the user directory
				return File.userDirectory.resolvePath(filePath);
				break;
			default:
				return undefined;
				break;
			}
		}
	}
}