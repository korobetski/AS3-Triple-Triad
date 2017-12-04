package tto.utils {
	import com.hurlant.crypto.symmetric.AESKey;
	import com.hurlant.util.Hex;
	import flash.display.Bitmap;
	import flash.geom.Rectangle;
	import flash.utils.ByteArray;
	public class CryptoHelper {
		[Embed(source = "../../../assets/tto_key.gif")] public static const ttoKey:Class, serrure:Rectangle = new Rectangle(0, 0, 31, 31);
		public function CryptoHelper() {}
		public static function encrypt(value:String):String {
			var plainText:ByteArray = Hex.toArray(Hex.fromString(value)),keyDatas:Bitmap = new ttoKey(),key:ByteArray = keyDatas.bitmapData.getPixels(serrure),aes:AESKey = new AESKey(key);
			aes.encrypt( plainText );
			return Hex.fromArray(plainText);
		}
		public static function decrypt(value:String):String {
			var encryptedText:String = value,keyDatas:Bitmap = new ttoKey(),key:ByteArray = keyDatas.bitmapData.getPixels(serrure),aes:AESKey = new AESKey(key),decryptedBytes:ByteArray = Hex.toArray( encryptedText );
			aes.decrypt( decryptedBytes );
			return decryptedBytes.toString();
		}
	}
}