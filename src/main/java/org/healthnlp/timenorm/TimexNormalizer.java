package org.healthnlp.timenorm;

import org.clulab.timenorm.scfg.Temporal;
import org.clulab.timenorm.scfg.TemporalExpressionParser;
import org.clulab.timenorm.scfg.TimeSpan;

import java.io.Closeable;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
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

    private final boolean _simpleFormat;

    private final ExecutorService _executor;

    /**
     * Simple Java interface for normalization of temporal expressions using TimeNorm.
     * A timeout is used for calls to TimeNorm to prevent hanging processes.
     * This will use a default timeout of 1 second and return simple normalization text.
     */
    public TimexNormalizer() {
        this( DEFAULT_TIMEOUT_MILLIS );
    }

    /**
     * Simple Java interface for normalization of temporal expressions using TimeNorm.
     * A timeout is used for calls to TimeNorm to prevent hanging processes.
     * This will return simple normalization text.
     * @param timeoutMillis Millisecond timeout for calls to TimeNorm to prevent hanging processes.
     * @throws IllegalArgumentException if the given timeout is less than 100 or greater than 10,000.
     */
    public TimexNormalizer( final int timeoutMillis ) throws IllegalArgumentException {
        this( timeoutMillis, true );
    }

    /**
     * Simple Java interface for normalization of temporal expressions using TimeNorm.
     * A timeout is used for calls to TimeNorm to prevent hanging processes.
     * This will use a default timeout of 1 second and return simple normalization text.
     * @param simpleFormat true if the returned normalization should be simple "2012-05-13",
     *                    rather than structured "TimeSpan(2026-06-15T00:00Z,2026-06-16T00:00Z,Period(Map(Days -> 1),Exact),Exact)"
     */
    public TimexNormalizer( final boolean simpleFormat ) {
        this( DEFAULT_TIMEOUT_MILLIS, simpleFormat );
    }

    /**
     * Simple Java interface for normalization of temporal expressions using TimeNorm.
     * A timeout is used for calls to TimeNorm to prevent hanging processes.
     * @param timeoutMillis Millisecond timeout for calls to TimeNorm to prevent hanging processes.
     * @param simpleFormat true if the returned normalization should be simple "2012-05-13",
     *                    rather than structured "TimeSpan(2026-06-15T00:00Z,2026-06-16T00:00Z,Period(Map(Days -> 1),Exact),Exact)"
     * @throws IllegalArgumentException if the given timeout is less than 100 or greater than 10,000.
     */
    public TimexNormalizer( final int timeoutMillis, final boolean simpleFormat ) throws IllegalArgumentException {
        if ( timeoutMillis < MIN_TIMEOUT_MILLIS || timeoutMillis > MAX_TIMEOUT_MILLIS ) {
            throw new IllegalArgumentException( "Timeout must be between "
                  + MIN_TIMEOUT_MILLIS + " and " + MAX_TIMEOUT_MILLIS );
        }
        _timeoutMillis = timeoutMillis;
        _simpleFormat = simpleFormat;
        _executor = Executors.newSingleThreadExecutor();
    }


    /**
     * Normalize time using today (right this minute) as the anchor date.
     * @param timex Text containing temporal expression.
     * @return Normalized expression of the given temporal expression.
     * @throws IllegalArgumentException if the temporal expression is empty or cannot be normalized.
     */
    public String normalize( final String timex ) throws IllegalArgumentException {
        return normalize( timex, LocalDateTime.now() );
    }

    /**
     * Normalize time using a provided anchor date.
     * @param timex Text containing temporal expression.
     * @param year year of the anchor date.
     * @param month month-of-year of the anchor date, from 1 to 12.
     * @param day day-of-month of the anchor date, from 1 to 31
     * @return Normalized expression of the given temporal expression.
     * @throws IllegalArgumentException if the temporal expression is empty or cannot be normalized or the anchor is bad.
     */
    public String normalize( final String timex, final int year, final int month, final int day )
          throws IllegalArgumentException {
        try {
            // Allow Java the luxury of checking date validity.
            final LocalDate date = LocalDate.of( year, month, day );
            return normalize( timex, date );
        } catch ( DateTimeException dtE ) {
            throw new IllegalArgumentException( dtE );
        }
    }

    /**
     * Normalize time using a provided anchor date.
     * @param timex Text containing temporal expression.
     * @param year year of the anchor date.
     * @param month month-of-year of the anchor date, from 1 to 12.
     * @param day day-of-month of the anchor date, from 1 to 31
     * @param hour hour-of-day of the anchor time, from 0 to 23
     * @param minute the minute-of-hour of the anchor time, from 0 to 59
     * @return Normalized expression of the given temporal expression.
     * @throws IllegalArgumentException if the temporal expression is empty or cannot be normalized or the anchor is bad.
     */
    public String normalize( final String timex, final int year, final int month, final int day,
                             final int hour, final int minute ) throws IllegalArgumentException {
        try {
            // Allow Java the luxury of checking date validity.
            final LocalDateTime datetime = LocalDateTime.of( year, month, day, hour, minute, 0 );
            return normalize( timex, datetime );
        } catch ( DateTimeException dtE ) {
            throw new IllegalArgumentException( dtE );
        }
    }


    /**
     * Normalize time using a provided anchor date.
     * @param timex Text containing temporal expression.
     * @param anchorDate The anchor time (required for resolving relative times like "today").
     * @return Normalized expression of the given temporal expression.
     * @throws IllegalArgumentException if the temporal expression is empty or cannot be normalized.
     */
    public String normalize( final String timex, final LocalDate anchorDate ) throws IllegalArgumentException {
        return normalize( timex,
              TimeSpan.of( anchorDate.getYear(), anchorDate.getMonthValue(), anchorDate.getDayOfMonth() ) );
    }

    /**
     * Normalize time using a provided anchor date.
     * @param timex Text containing temporal expression.
     * @param anchorTime The anchor time (required for resolving relative times like "in an hour").
     * @return Normalized expression of the given temporal expression.
     * @throws IllegalArgumentException if the temporal expression is empty or cannot be normalized.
     */
    public String normalize( final String timex, final LocalDateTime anchorTime ) throws IllegalArgumentException {
        return normalize( timex,
              TimeSpan.of( anchorTime.getYear(), anchorTime.getMonthValue(), anchorTime.getDayOfMonth(),
                    anchorTime.getHour(), anchorTime.getMinute(), anchorTime.getSecond() ) );
    }

    /**
     * Normalize time using a provided anchor date.
     * @param timex Text containing temporal expression.
     * @param anchorTime The anchor time (required for resolving relative times like "today").
     * @return Normalized expression of the given temporal expression.
     * @throws IllegalArgumentException if the temporal expression is empty or cannot be normalized.
     */
    public String normalize( final String timex, final TimeSpan anchorTime ) throws IllegalArgumentException {
        if ( timex.isBlank() ) {
            throw new IllegalArgumentException( "Cannot normalize an empty Temporal Expression." );
        }
        final String tempex = String.join( " ", WHITESPACE_PATTERN.split( timex ) );
        final Callable<String> callable = new TimeNormCallable( tempex, anchorTime, _simpleFormat );
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
     *
     * @return true if normalization returns simple text, e.g. 2012-05-13"
     *         rather than structured "TimeSpan(2026-06-15T00:00Z,2026-06-16T00:00Z,Period(Map(Days -> 1),Exact),Exact)"
     */
    public boolean isSimpleFormat() {
        return _simpleFormat;
    }

    /**
     * shut down the executor.
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
        final private boolean __simpleFormat;

        /**
         *
         * @param timex Text containing temporal expression.
         * @param anchorTime The anchor time (required for resolving relative times like "today").
         * @param simpleFormat true if the returned normalization should be simple "2012-05-13",
         *                    rather than structured "TimeSpan(2026-06-15T00:00Z,2026-06-16T00:00Z,Period(Map(Days -> 1),Exact),Exact)"
         */
        private TimeNormCallable( final String timex, final TimeSpan anchorTime, final boolean simpleFormat ) {
            __timex = timex;
            __anchorTime = anchorTime;
            __simpleFormat = simpleFormat;
        }

        /**
         * {@inheritDoc}
         *
         * @return normalized version of the temporal expression.
         */
        @Override
        public String call() {
            final Temporal t = NORMALIZER.parse( __timex, __anchorTime ).get();
            return __simpleFormat ? t.timeMLValue() : t.toString();
        }
    }

}
