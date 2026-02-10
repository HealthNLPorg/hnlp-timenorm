package org.healthnlp.timenorm;

import org.clulab.timenorm.scfg.Temporal;
import org.clulab.timenorm.scfg.TemporalExpressionParser;
import org.clulab.timenorm.scfg.TimeSpan;

import java.io.Closeable;
import java.util.concurrent.*;
import java.util.logging.Logger;
import java.util.regex.Pattern;


/**
 * Simple Java interface for normalization of temporal expressions using TimeNorm.
 * A timeout is used for calls to TimeNorm to prevent hanging processes.
 */
final public class TimexNormalizer implements Closeable {
    static private final Logger LOGGER = Logger.getLogger( "TimexNormalizer" );

    // Use the TimeNorm English parser.
    static private final TemporalExpressionParser NORMALIZER = TemporalExpressionParser.en();

    static private final Pattern WHITESPACE_PATTERN = Pattern.compile( "\\s+" );

    // How long to wait before canceling the operation for normalizing a time mention.
    static private final int DEFAULT_TIMEOUT_MILLIS = 1000;
    static private final int MIN_TIMEOUT_MILLIS = 100;
    static private final int MAX_TIMEOUT_MILLIS = 10000;
    private final int _timeoutMillis;

    private final ExecutorService _executor;

    /**
     * Simple Java interface for normalization of temporal expressions using TimeNorm.
     * A timeout is used for calls to TimeNorm to prevent hanging processes.
     * This will use a default timeout of 1 second.
     */
    public TimexNormalizer() {
        this( DEFAULT_TIMEOUT_MILLIS );
    }

    /**
     * Simple Java interface for normalization of temporal expressions using TimeNorm.
     * @param timeoutMillis Millisecond timeout for calls to TimeNorm to prevent hanging processes.
     */
    public TimexNormalizer( final int timeoutMillis ) {
        if ( timeoutMillis < MIN_TIMEOUT_MILLIS || timeoutMillis > MAX_TIMEOUT_MILLIS ) {
            throw new IllegalArgumentException( "Timeout must be between "
                  + MIN_TIMEOUT_MILLIS + " and " + MAX_TIMEOUT_MILLIS );
        }
        _timeoutMillis = timeoutMillis;
        LOGGER.info( "Using timeout: " + _timeoutMillis + " milliseconds." );
        _executor = Executors.newSingleThreadExecutor();
    }

    /**
     *
     * @param timex Text containing temporal expression.
     * @return Normalized expression of the given temporal expression.
     */
    public String getNormalizedTimex( final String timex ) {
        return getNormalizedTimex( timex, null );
    }

    /**
     *
     * @param timex Text containing temporal expression.
     * @param referenceTime TimeNorm TimeSpan object representing some reference time for normalization.
     * @return Normalized expression of the given temporal expression.
     */
    public String getNormalizedTimex( final String timex, final TimeSpan referenceTime ) {
        if ( timex.isBlank() ) {
            return "";
        }
        final String tempex = String.join( " ", WHITESPACE_PATTERN.split( timex ) );
        final Callable<String> callable = new TimeNormCallable( tempex, referenceTime );
        final Future<String> future = _executor.submit( callable );
        try {
            return future.get( _timeoutMillis, TimeUnit.MILLISECONDS );
        } catch ( InterruptedException | ExecutionException | TimeoutException multE ) {
            LOGGER.fine( "Timeout at " + _timeoutMillis + " milliseconds for text " + timex );
            if ( !future.cancel( true ) ) {
                LOGGER.severe( "Timed out but could not be cancelled while normalizing text " + timex );
            }
        }
        if ( future.isCancelled() ) {
            LOGGER.severe( "Cancelled while normalizing text " + timex );
        } else if ( !future.isDone() ) {
            LOGGER.severe( "Not cancelled but didn't complete while normalizing text " + timex );
        }
        return "";
    }

    /**
     * shut down the executor
     * {@inheritDoc}
     */
    @Override
    public void close() {
        _executor.shutdownNow();
    }


    /**
     * Simple Callable that runs TimeNorm normalization so that it can be interrupted.
     */
    static private final class TimeNormCallable implements Callable<String> {
        final private String __timex;
        final private TimeSpan __referenceTime;

        /**
         *
         * @param timex Text containing temporal expression.
         */
        private TimeNormCallable( final String timex ) {
            this( timex, null );
        }

        /**
         *
         * @param timex Text containing temporal expression.
         * @param referenceTime TimeNorm TimeSpan object representing some reference time for normalization.
         */
        private TimeNormCallable( final String timex, final TimeSpan referenceTime ) {
            __timex = timex;
            __referenceTime = referenceTime;
        }

        /**
         * {@inheritDoc}
         *
         * @return TimeNorm Temporal object.
         */
        @Override
        public String call() {
            return NORMALIZER.parse( __timex, __referenceTime ).get().timeMLValue();
        }
    }

}
