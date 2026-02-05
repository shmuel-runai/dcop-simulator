package crypto.utils;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;

import sinalgo.runtime.Global;

public class PaillierMgr {
	private Map<String, Paillier> pailliers;
	
	public PaillierMgr() {
		pailliers = new HashMap<String, Paillier>();
	}

	public void put(String key, Paillier paillier) {
		pailliers.put(key, paillier);
	}

	public Paillier get(String key) {
		return pailliers.get(key);
	}

	public void clear() {
		pailliers.clear();
	}
	
	public BigInteger Encryption(String key, BigInteger value) {
		return pailliers.get(key).Encryption(value);
	}
	
	public BigInteger Decryption(String key, BigInteger value) {
		return pailliers.get(key).Decryption(value);
	}

	public void logStatus() {
		for (String key : this.pailliers.keySet()) {
			debug(false, "Paillier key: " + key);
		}		

	}
	
	private void debug(boolean flag, String logStr) {
		Global.log.logln(flag, logStr);
	}

}
