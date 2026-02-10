package org.healthnlp.timenorm;


/**
 * Simple test for normalization.
 * As there is one single test on two methods in one class we are not using a test harness, junit or other.
 * This is a simple runnable class that instantiates some TimexNormalizers and calls them with test text.
 * @author SPF , chip-nlp
 * @since {2/10/2026}
 */
public class TimexNormalizerTest {

   static private final Integer[] TIMEOUTS = { 100, 5000, 10000 };
   static private final String[] GOOD_TIMEXES
         = { "Wednesday", "March, 2000", "Christmas", "noon", "last week", "4/14/2022", "5/13, 2012", "6-15", "tomorrow" };
   // It seems to me that "n o'clock" should be easy to normalize.
   static private final String[] BAD_TIMEXES
         = { "", "Tuesday, 1999", "before noon", "some   time   today", "5 o'clock", "5 o'clock tomorrow", "5 oclock tomorrow"  };

   public static void main( String[] args ) {
      System.out.println( "===== Testing TimexNormalizer with default constructor (default timeout). =====");
      try ( TimexNormalizer normalizer = new TimexNormalizer() ) {
         normalizeGood( normalizer );
         normalizeBad( normalizer );
      }
      System.out.println( "\n===== Testing TimexNormalizer constructed with ascending timeouts. =====");
      for ( int timeout : TIMEOUTS ) {
         try ( TimexNormalizer normalizer = new TimexNormalizer( timeout ) ) {
            normalizeGood( normalizer );
//            normalizeBad( normalizer );
         }
      }
   }

   static private void normalizeGood( final TimexNormalizer normalizer ) {
      System.out.println( "\n=== Using well-formed Temporal Expressions. ===");
      for ( String timex : GOOD_TIMEXES ) {
         normalize( normalizer, timex );
      }
   }

   static private void normalizeBad( final TimexNormalizer normalizer ) {
      System.out.println( "\n=== Using poorly-formed Temporal Expressions. ===");
      for ( String timex : BAD_TIMEXES ) {
         normalize( normalizer, timex );
      }
   }


   static private void normalize( final TimexNormalizer normalizer, final String timex ) {
      try {
         final String result = normalizer.getNormalizedTimex( timex );
         if ( !result.isEmpty() ) {
            System.out.println( timex + " is normalized to " + result + " in " + normalizer.getTimeout() + " milliseconds" );
         }
      } catch ( IllegalArgumentException iaE ) {
         System.err.println( iaE.getMessage() );
         if ( iaE.getCause() != null ) {
            System.err.println( iaE.getCause().getMessage() + "\n" );
         }
      }
   }


}
