package org.healthnlp.timenorm;

import org.clulab.timenorm.scfg.TemporalExpressionParser;
import org.clulab.timenorm.scfg.TimeSpan;

import java.io.Closeable;
import java.time.LocalDate;
import java.util.concurrent.*;
import java.util.regex.Pattern;


/**
 * Simple Java interface for normalization of temporal expressions using TimeNorm.
 * A timeout is used for calls to TimeNorm to prevent hanging processes.
 * Viewed in the JetBrains IntelliJ IDE, you may see indications of missing classes for the TimeNorm scala class imports.
 * If this is the case, make sure that you have the JetBrain Scala plugin installed and enabled.
 * Right-click on the project/module in your "Project" window, and select Maven > "Generate Sources and Update Folders".
 */
final public class TimexNormalizer implements Closeable {

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
     * @throws IllegalArgumentException if the given timeout is less than 100 or greater than 10,000.
     */
    public TimexNormalizer( final int timeoutMillis ) throws IllegalArgumentException {
        if ( timeoutMillis < MIN_TIMEOUT_MILLIS || timeoutMillis > MAX_TIMEOUT_MILLIS ) {
            throw new IllegalArgumentException( "Timeout must be between "
                  + MIN_TIMEOUT_MILLIS + " and " + MAX_TIMEOUT_MILLIS );
        }
        _timeoutMillis = timeoutMillis;
        _executor = Executors.newSingleThreadExecutor();
    }

    /**
     *
     * @param timex Text containing temporal expression.
     * @return Normalized expression of the given temporal expression.
     * @throws IllegalArgumentException if the temporal expression is empty or cannot be normalized.
     */
    public String getNormalizedTimex( final String timex ) throws IllegalArgumentException {
        return getNormalizedTimex( timex, null );
    }

    /**
     *
     * @param timex Text containing temporal expression.
     * @param anchorTime The anchor time (required for resolving relative times like "today").
     * @return Normalized expression of the given temporal expression.
     * @throws IllegalArgumentException if the temporal expression is empty or cannot be normalized.
     */
    public String getNormalizedTimex( final String timex, final TimeSpan anchorTime ) throws IllegalArgumentException {
        if ( timex.isBlank() ) {
            throw new IllegalArgumentException( "Cannot normalize an empty Temporal Expression." );
        }
        final String tempex = String.join( " ", WHITESPACE_PATTERN.split( timex ) );
        final Callable<String> callable = new TimeNormCallable( tempex, anchorTime );
        final Future<String> future = _executor.submit( callable );
        try {
            return future.get( _timeoutMillis, TimeUnit.MILLISECONDS );
        } catch ( InterruptedException | ExecutionException | TimeoutException multE ) {
            // An exception will be thrown that is equal to an UnsupportedOperationException thrown by TimeNorm.
            // However, it is somehow cast to another type.  Need to recognize it by its message.
            if ( multE.getMessage().contains( "UnsupportedOperationException" ) ) {
                throw new IllegalArgumentException( "Unable to normalize temporal expression " + timex, multE );
            } else {
                throw new IllegalArgumentException( "Normalization timed out at " + _timeoutMillis + " milliseconds on temporal expression " + timex, multE );
            }
        }
    }


    /**
     *
     * @return the timeout in milliseconds used by this normalizer.
     */
    public int getTimeout() {
        return _timeoutMillis;
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
        final private TimeSpan __anchorTime;

        /**
         *
         * @param timex Text containing temporal expression.
         * @param anchorTime The anchor time (required for resolving relative times like "today").
         */
        private TimeNormCallable( final String timex, final TimeSpan anchorTime ) {
            __timex = timex;
            if ( anchorTime != null ) {
                __anchorTime = anchorTime;
            } else {
                final LocalDate today = LocalDate.now();
                __anchorTime = TimeSpan.of( today.getYear(), today.getMonthValue(), today.getDayOfMonth() );
            }
        }

        /**
         * {@inheritDoc}
         *
         * @return TimeNorm Temporal object.
         */
        @Override
        public String call() {
            return NORMALIZER.parse( __timex, __anchorTime ).get().timeMLValue();
        }
    }

}
