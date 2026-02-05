package crypto.utils;
/**
 * This program is free software: you can redistribute it and/or modify it 
 * under the terms of the GNU General Public License as published by the Free 
 * Software Foundation, either version 3 of the License, or (at your option) 
 * any later version. 
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT 
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or 
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for 
 * more details. 
 * 
 * You should have received a copy of the GNU General Public License along with 
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */

import java.math.*;
import java.util.*;

/**
 * Paillier Cryptosystem <br><br>
 * References: <br>
 * [1] Pascal Paillier, "Public-Key Cryptosystems Based on Composite Degree Residuosity Classes," EUROCRYPT'99.
 *    URL: <a href="http://www.gemplus.com/smart/rd/publications/pdf/Pai99pai.pdf">http://www.gemplus.com/smart/rd/publications/pdf/Pai99pai.pdf</a><br>
 * 
 * [2] Paillier cryptosystem from Wikipedia. 
 *    URL: <a href="http://en.wikipedia.org/wiki/Paillier_cryptosystem">http://en.wikipedia.org/wiki/Paillier_cryptosystem</a>
 * @author Kun Liu (kunliu1@cs.umbc.edu)
 * @version 1.0
 */
public class Paillier {

    /**
     * p and q are two large primes. 
     * lambda = lcm(p-1, q-1) = (p-1)*(q-1)/gcd(p-1, q-1).
     */
    private BigInteger p,  q,  lambda;
    /**
     * n = p*q, where p and q are two large primes.
     */
    public BigInteger n;
    /**
     * nsquare = n*n
     */
    public BigInteger nsquare;
    /**
     * a random integer in Z*_{n^2} where gcd (L(g^lambda mod n^2), n) = 1.
     */
    private BigInteger g;
    /**
     * number of bits of modulus
     */
    private int bitLength;

    /**
     * Constructs an instance of the Paillier cryptosystem.
     * @param bitLengthVal number of bits of modulus
     * @param certainty The probability that the new BigInteger represents a prime number will exceed (1 - 2^(-certainty)). The execution time of this constructor is proportional to the value of this parameter.
     */
    public Paillier(int bitLengthVal, int certainty) {
        KeyGeneration(bitLengthVal, certainty);
    }

    /**
     * Constructs an instance of the Paillier cryptosystem with 512 bits of modulus and at least 1-2^(-64) certainty of primes generation.
     */
    public Paillier() {
        KeyGeneration(512, 64);
    }

    /**
     * Sets up the public key and private key.
     * @param bitLengthVal number of bits of modulus.
     * @param certainty The probability that the new BigInteger represents a prime number will exceed (1 - 2^(-certainty)). The execution time of this constructor is proportional to the value of this parameter.
     */
    public void KeyGeneration(int bitLengthVal, int certainty) {
        bitLength = bitLengthVal;
        /*Constructs two randomly generated positive BigIntegers that are probably prime, with the specified bitLength and certainty.*/
        p = new BigInteger(bitLength / 2, certainty, new Random());
        q = new BigInteger(bitLength / 2, certainty, new Random());

        n = p.multiply(q);
        nsquare = n.multiply(n);

        g = new BigInteger("2");
        lambda = p.subtract(BigInteger.ONE).multiply(q.subtract(BigInteger.ONE)).divide(
                p.subtract(BigInteger.ONE).gcd(q.subtract(BigInteger.ONE)));
        /* check whether g is good.*/
        if (g.modPow(lambda, nsquare).subtract(BigInteger.ONE).divide(n).gcd(n).intValue() != 1) {
            System.out.println("g is not good. Choose g again.");
            System.exit(1);
        }
    }

    /**
     * Encrypts plaintext m. ciphertext c = g^m * r^n mod n^2. This function explicitly requires random input r to help with encryption.
     * @param m plaintext as a BigInteger
     * @param r random plaintext to help with encryption
     * @return ciphertext as a BigInteger
     */
    public BigInteger Encryption(BigInteger m, BigInteger r) {
        return g.modPow(m, nsquare).multiply(r.modPow(n, nsquare)).mod(nsquare);
    }

    /**
     * Encrypts plaintext m. ciphertext c = g^m * r^n mod n^2. This function automatically generates random input r (to help with encryption).
     * @param m plaintext as a BigInteger
     * @return ciphertext as a BigInteger
     */
    public BigInteger Encryption(BigInteger m) {
        BigInteger r = new BigInteger(bitLength, new Random());
        return g.modPow(m, nsquare).multiply(r.modPow(n, nsquare)).mod(nsquare);

    }

    /**
     * Decrypts ciphertext c. plaintext m = L(c^lambda mod n^2) * u mod n, where u = (L(g^lambda mod n^2))^(-1) mod n.
     * @param c ciphertext as a BigInteger
     * @return plaintext as a BigInteger
     */
    public BigInteger Decryption(BigInteger c) {
        BigInteger u = g.modPow(lambda, nsquare).subtract(BigInteger.ONE).divide(n).modInverse(n);
        return c.modPow(lambda, nsquare).subtract(BigInteger.ONE).divide(n).multiply(u).mod(n);
    }

    /**
     * main function
     * @param str intput string
     */
    public static void main(String[] str) {
    	long startTime;
        /* instantiating an object of Paillier cryptosystem*/
        Paillier paillier = new Paillier();
        /* instantiating two plaintext msgs*/
        BigInteger m1 = new BigInteger("20");
        BigInteger m2 = new BigInteger("60");
        /* encryption*/
        startTime = System.nanoTime();
        BigInteger em1 = paillier.Encryption(m1);
        BigInteger em2 = paillier.Encryption(m2);
        System.out.println("Nanos: "+(System.nanoTime()-startTime)); 
        /* printout encrypted text*/
        System.out.println(em1);
        System.out.println(em2);
        /* printout decrypted text */
        startTime = System.nanoTime();
        paillier.Decryption(em1);
        paillier.Decryption(em2);
        System.out.println("Nanos: "+(System.nanoTime()-startTime));
        startTime = System.nanoTime();
        em1 = paillier.Encryption(m1);
        em2 = paillier.Encryption(m2);
        System.out.println("Nanos: "+(System.nanoTime()-startTime));
        startTime = System.nanoTime();
        paillier.Decryption(em1);
        paillier.Decryption(em2);
        System.out.println("Nanos: "+(System.nanoTime()-startTime));
        System.out.println(paillier.Decryption(em1).toString());
        System.out.println(paillier.Decryption(em2).toString());
        
        
        /* test homomorphic properties -> D(E(m1)*E(m2) mod n^2) = (m1 + m2) mod n */
        BigInteger product_em1em2 = em1.multiply(em2).mod(paillier.nsquare);
        BigInteger sum_m1m2 = m1.add(m2).mod(paillier.n);
        System.out.println("original sum: " + sum_m1m2.toString());
        System.out.println("decrypted sum: " + paillier.Decryption(product_em1em2).toString());

        /* test homomorphic properties -> D(E(m1)^m2 mod n^2) = (m1*m2) mod n */
        BigInteger expo_em1m2 = em1.modPow(m2, paillier.nsquare);
        BigInteger prod_m1m2 = m1.multiply(m2).mod(paillier.n);
        System.out.println("original product: " + prod_m1m2.toString());
        System.out.println("decrypted product: " + paillier.Decryption(expo_em1m2).toString());

        BigInteger g,p,a;
        
        Random rand = new Random();
		g = new BigInteger(128,rand);
		p = new BigInteger(128,rand);
		if (g.compareTo(p) > 0)
		{
			a = g;
			g = p;
			p = a;
		}
		g.modPow(g,p);
		
		(g.multiply(g)).mod(p);
		
		System.out.println("\n*********************\nExperiment 1:");
		testEncryptionCosts(1000);
		System.out.println("\n*********************\nExperiment 2:");
		testEncryptionCosts2(1000,1000);
        
    }
    
    @SuppressWarnings("unused")
	public static void testEncryptionCosts(int iterations) {
    	long tEncrypt=0, tDecrypt=0, tAddP=0, tAddLong=0, tAddInt=0, tMultP=0, tMultLong=0, tCompP=0, tCompLong=0, tCompInt=0, tGenP=0, tGenLong=0, tGenInt=0, tCryptoSys=0, tTimeChk=0;; 
    	long startTime;
    	Random r = new Random(); 
    	
    	for (int iter=0; iter<iterations; iter++) {
    		/* Checking the time it takes to test the times */
    		startTime = System.nanoTime();
    		tTimeChk += (System.nanoTime() - startTime);
    		
    		/* Checking the time it takes to instantiate an object of Paillier cryptosystem */
    		startTime = System.nanoTime();
    		Paillier paillier = new Paillier();
    		tCryptoSys += (System.nanoTime() - startTime);
    		
    		/* Checking the time it takes to generate a random integer */
    		startTime = System.nanoTime();
    		int iA = r.nextInt();
    		tGenInt += (System.nanoTime() - startTime);
    		int iB = r.nextInt();
    		
    		/* Checking the time it takes to generate a random long */
    		startTime = System.nanoTime();
    		long lA = r.nextLong();
    		tGenLong += (System.nanoTime() - startTime);
    		long lB = r.nextLong();   		
    		
    		/* Checking the time it takes to generate a random 128-bit p */
    		startTime = System.nanoTime();
    		BigInteger pA = new BigInteger(128,r);
    		tGenP += (System.nanoTime() - startTime);
    		BigInteger pB = new BigInteger(128,r);  		
    		
    		/* Checking the time it takes to add integers */
    		startTime = System.nanoTime();
    		int iC = iA + iB;
    		tAddInt += (System.nanoTime() - startTime);
    		
    		/* Checking the time it takes to add longs */
    		startTime = System.nanoTime();
    		long lC = lA + lB;
    		tAddLong += (System.nanoTime() - startTime);
  		
    		/* Checking the time it takes to add 128-bit p's */
    		startTime = System.nanoTime();
    		BigInteger pC = pA.add(pB);
    		tAddP += (System.nanoTime() - startTime);
    		
    		/* Checking the time it takes to multiply longs */
    		startTime = System.nanoTime();
    		lC = lA * lB;
    		tMultLong += (System.nanoTime() - startTime);
  	
    		/* Checking the time it takes to modulo multiply 128-bit p's */
    		startTime = System.nanoTime();
    		pC = (pA.multiply(pB)).mod(pC);
    		tMultP += (System.nanoTime() - startTime);
    		
    		/* Checking the time it takes to compare integers */
    		startTime = System.nanoTime();
    		if (iA > iB) {
    			tCompInt += (System.nanoTime() - startTime);
    			iA++;
    		}
    		else {
    			tCompInt += (System.nanoTime() - startTime);
    			iB++;
    		}
    		
    		/* Checking the time it takes to compare longs */
    		startTime = System.nanoTime();
    		if (lA > lB) {
    			tCompLong += (System.nanoTime() - startTime);
    			lA++;
    		}
    		else {
    			tCompLong += (System.nanoTime() - startTime);
    			lB++;
    		}
    		
    		/* Checking the time it takes to compare 128-bit p's */
    		startTime = System.nanoTime();
    		if (pA.compareTo(pB) > 0) {
    			tCompP += (System.nanoTime() - startTime);
    			lA++;
    		}
    		else {
    			tCompP += (System.nanoTime() - startTime);
    			lB++;
    		}   		
		
    		/* Checking the time it takes to encrypt a 128-bit p using Pailler */
    		startTime = System.nanoTime();
    		pC = paillier.Encryption(pA);
    		tEncrypt += (System.nanoTime() - startTime);
    		
    		/* Checking the time it takes to decrypt a 128-bit p using Pailler */
    		startTime = System.nanoTime();
    		pB = paillier.Decryption(pC);
    		tDecrypt += (System.nanoTime() - startTime);
    	}
    	
    	System.out.println("Average test overhead: "+(tTimeChk/iterations)+" nanoseconds");
    	System.out.println("Average integer generation: "+(tGenInt/iterations)+" nanoseconds");
    	System.out.println("Average long generation: "+(tGenLong/iterations)+" nanoseconds");
    	System.out.println("Average 128-bit generation: "+(tGenP/iterations)+" nanoseconds");
    	System.out.println("Average integer addition: "+(tAddInt/iterations)+" nanoseconds");
    	System.out.println("Average long addition: "+(tAddLong/iterations)+" nanoseconds");
    	System.out.println("Average 128-bit addition: "+(tAddP/iterations)+" nanoseconds");
    	System.out.println("Average long multiplication: "+(tMultLong/iterations)+" nanoseconds");
    	System.out.println("Average 128-bit modulo multiplication: "+(tMultP/iterations)+" nanoseconds");
    	System.out.println("Average integer comparison: "+(tCompInt/iterations)+" nanoseconds");
    	System.out.println("Average long comparison: "+(tCompLong/iterations)+" nanoseconds");
    	System.out.println("Average 128-bit comparison: "+(tCompP/iterations)+" nanoseconds");
    	System.out.println("Average cryptosystem instantiation: "+(tCryptoSys/iterations)+" nanoseconds");
    	System.out.println("Average encryption: "+(tEncrypt/iterations)+" nanoseconds");
    	System.out.println("Average decryption: "+(tDecrypt/iterations)+" nanoseconds");
    }
    
    @SuppressWarnings("unused")
	public static void testEncryptionCosts2(int iterations, int innerIter) {
    	long tEncrypt=0, tDecrypt=0, tAddP=0, tAddLong=0, tAddInt=0, tMultP=0, tMultLong=0, tCompP=0, tCompLong=0, tCompInt=0, tGenP=0, tGenLong=0, tGenInt=0, tCryptoSys=0, tTimeChk=0;; 
    	long startTime;
    	Random r = new Random(); 
    	
    	for (int iter=0; iter<iterations; iter++) {
    		/* Checking the time it takes to test the times */
    		startTime = System.nanoTime();
    		tTimeChk += (System.nanoTime() - startTime);
    		
    		/* Checking the time it takes to instantiate an object of Paillier cryptosystem */
    		startTime = System.nanoTime();
    		Paillier paillier = new Paillier();
    		tCryptoSys += (System.nanoTime() - startTime);
    		
    		/* Checking the time it takes to generate a random integer */
    		startTime = System.nanoTime();
    		int iA=0;
    		for (int i=0; i<innerIter; i++)
    			iA = r.nextInt();
    		tGenInt += ((System.nanoTime() - startTime)/innerIter);
    		int iB = r.nextInt();
    		
    		/* Checking the time it takes to generate a random long */
    		startTime = System.nanoTime();
    		long lA=0;
    		for (int i=0; i<innerIter; i++)
    			lA = r.nextLong();
    		tGenLong += ((System.nanoTime() - startTime)/innerIter);
    		long lB = r.nextLong();   		
    		
    		/* Checking the time it takes to generate a random 128-bit p */
    		startTime = System.nanoTime();
    		BigInteger pA=null;
    		for (int i=0; i<innerIter; i++)
    			pA = new BigInteger(128,r);
    		tGenP += ((System.nanoTime() - startTime)/innerIter);
    		BigInteger pB = new BigInteger(128,r);  	
    		
    		/* Checking the time it takes to add integers */
    		startTime = System.nanoTime();
    		int iC;
    		for (int i=0; i<innerIter; i++)
    			iC = iA + iB;
    		tAddInt += ((System.nanoTime() - startTime)/innerIter);
    		
    		/* Checking the time it takes to add longs */
    		startTime = System.nanoTime();
    		long lC;
    		for (int i=0; i<innerIter; i++)	
    			lC = lA + lB;
    		tAddLong += ((System.nanoTime() - startTime)/innerIter);
  		
    		/* Checking the time it takes to add 128-bit p's */
    		startTime = System.nanoTime();
    		BigInteger pC=null;
    		for (int i=0; i<innerIter; i++)	
    			pC = pA.add(pB);
    		tAddP += ((System.nanoTime() - startTime)/innerIter);
    		
    		/* Checking the time it takes to multiply longs */
    		startTime = System.nanoTime();
    		for (int i=0; i<innerIter; i++)
    			lC = lA * lB;
    		tMultLong += ((System.nanoTime() - startTime)/innerIter);
  	
    		/* Checking the time it takes to modulo multiply 128-bit p's */
    		startTime = System.nanoTime();
    		for (int i=0; i<innerIter; i++)
    			pC = (pA.multiply(pB)).mod(pB);
    		tMultP += ((System.nanoTime() - startTime)/innerIter);
    		
    		/* Checking the time it takes to compare integers */
    		startTime = System.nanoTime();
    		for (int i=0; i<innerIter; i++)
    			if (iA > iB) {}
    		tCompInt += ((System.nanoTime() - startTime)/innerIter);
    		
    		/* Checking the time it takes to compare longs */
    		startTime = System.nanoTime();
    		for (int i=0; i<innerIter; i++)
    			if (lA > lB) {}
    		tCompLong += ((System.nanoTime() - startTime)/innerIter);
    		
    		/* Checking the time it takes to compare 128-bit p's */
    		startTime = System.nanoTime();
    		for (int i=0; i<innerIter; i++)
    			if (pA.compareTo(pB) > 0) {}
     		tCompP += ((System.nanoTime() - startTime)/innerIter);
		
    		/* Checking the time it takes to encrypt a 128-bit p using Pailler */
    		startTime = System.nanoTime();
    		pC = paillier.Encryption(pA);
    		tEncrypt += (System.nanoTime() - startTime);
    		
    		/* Checking the time it takes to decrypt a 128-bit p using Pailler */
    		startTime = System.nanoTime();
    		pB = paillier.Decryption(pC);
    		tDecrypt += (System.nanoTime() - startTime);
    	}
    	
    	System.out.println("Average test overhead: "+(tTimeChk/iterations)+" nanoseconds");
    	System.out.println("Average integer generation: "+(tGenInt/iterations)+" nanoseconds");
    	System.out.println("Average long generation: "+(tGenLong/iterations)+" nanoseconds");
    	System.out.println("Average 128-bit generation: "+(tGenP/iterations)+" nanoseconds");
    	System.out.println("Average integer addition: "+(tAddInt/iterations)+" nanoseconds");
    	System.out.println("Average long addition: "+(tAddLong/iterations)+" nanoseconds");
    	System.out.println("Average 128-bit addition: "+(tAddP/iterations)+" nanoseconds");
    	System.out.println("Average long multiplication: "+(tMultLong/iterations)+" nanoseconds");
    	System.out.println("Average 128-bit modulo multiplication: "+(tMultP/iterations)+" nanoseconds");
    	System.out.println("Average integer comparison: "+(tCompInt/iterations)+" nanoseconds");
    	System.out.println("Average long comparison: "+(tCompLong/iterations)+" nanoseconds");
    	System.out.println("Average 128-bit comparison: "+(tCompP/iterations)+" nanoseconds");
    	System.out.println("Average cryptosystem instantiation: "+(tCryptoSys/iterations)+" nanoseconds");
    	System.out.println("Average encryption: "+(tEncrypt/iterations)+" nanoseconds");
    	System.out.println("Average decryption: "+(tDecrypt/iterations)+" nanoseconds");
    }  
    
   
}
