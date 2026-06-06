package com.posturebot.app.data.db;

import android.database.Cursor;
import android.os.CancellationSignal;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.CoroutinesRoom;
import androidx.room.EntityDeletionOrUpdateAdapter;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import java.lang.Class;
import java.lang.Exception;
import java.lang.Float;
import java.lang.Integer;
import java.lang.Long;
import java.lang.Object;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import javax.annotation.processing.Generated;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import kotlinx.coroutines.flow.Flow;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class PostureDao_Impl implements PostureDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<PostureSample> __insertionAdapterOfPostureSample;

  private final EntityInsertionAdapter<Session> __insertionAdapterOfSession;

  private final EntityDeletionOrUpdateAdapter<Session> __updateAdapterOfSession;

  public PostureDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfPostureSample = new EntityInsertionAdapter<PostureSample>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR ABORT INTO `posture_samples` (`id`,`sessionId`,`timestamp`,`forwardHeadOffsetPx`,`neckInclinationDeg`,`headTiltDeg`,`shoulderSymmetryPx`,`state`) VALUES (nullif(?, 0),?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final PostureSample entity) {
        statement.bindLong(1, entity.getId());
        statement.bindString(2, entity.getSessionId());
        statement.bindLong(3, entity.getTimestamp());
        statement.bindDouble(4, entity.getForwardHeadOffsetPx());
        statement.bindDouble(5, entity.getNeckInclinationDeg());
        statement.bindDouble(6, entity.getHeadTiltDeg());
        statement.bindDouble(7, entity.getShoulderSymmetryPx());
        statement.bindString(8, entity.getState());
      }
    };
    this.__insertionAdapterOfSession = new EntityInsertionAdapter<Session>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR ABORT INTO `sessions` (`sessionId`,`startTime`,`endTime`,`goodPercent`,`totalAlerts`) VALUES (?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final Session entity) {
        statement.bindString(1, entity.getSessionId());
        statement.bindLong(2, entity.getStartTime());
        if (entity.getEndTime() == null) {
          statement.bindNull(3);
        } else {
          statement.bindLong(3, entity.getEndTime());
        }
        if (entity.getGoodPercent() == null) {
          statement.bindNull(4);
        } else {
          statement.bindDouble(4, entity.getGoodPercent());
        }
        if (entity.getTotalAlerts() == null) {
          statement.bindNull(5);
        } else {
          statement.bindLong(5, entity.getTotalAlerts());
        }
      }
    };
    this.__updateAdapterOfSession = new EntityDeletionOrUpdateAdapter<Session>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "UPDATE OR ABORT `sessions` SET `sessionId` = ?,`startTime` = ?,`endTime` = ?,`goodPercent` = ?,`totalAlerts` = ? WHERE `sessionId` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final Session entity) {
        statement.bindString(1, entity.getSessionId());
        statement.bindLong(2, entity.getStartTime());
        if (entity.getEndTime() == null) {
          statement.bindNull(3);
        } else {
          statement.bindLong(3, entity.getEndTime());
        }
        if (entity.getGoodPercent() == null) {
          statement.bindNull(4);
        } else {
          statement.bindDouble(4, entity.getGoodPercent());
        }
        if (entity.getTotalAlerts() == null) {
          statement.bindNull(5);
        } else {
          statement.bindLong(5, entity.getTotalAlerts());
        }
        statement.bindString(6, entity.getSessionId());
      }
    };
  }

  @Override
  public Object insertSample(final PostureSample sample,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfPostureSample.insert(sample);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object insertSession(final Session session, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfSession.insert(session);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object updateSession(final Session session, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __updateAdapterOfSession.handle(session);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Flow<List<PostureSample>> getSamplesForSession(final String sessionId) {
    final String _sql = "SELECT * FROM posture_samples WHERE sessionId = ? ORDER BY timestamp ASC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, sessionId);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"posture_samples"}, new Callable<List<PostureSample>>() {
      @Override
      @NonNull
      public List<PostureSample> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfSessionId = CursorUtil.getColumnIndexOrThrow(_cursor, "sessionId");
          final int _cursorIndexOfTimestamp = CursorUtil.getColumnIndexOrThrow(_cursor, "timestamp");
          final int _cursorIndexOfForwardHeadOffsetPx = CursorUtil.getColumnIndexOrThrow(_cursor, "forwardHeadOffsetPx");
          final int _cursorIndexOfNeckInclinationDeg = CursorUtil.getColumnIndexOrThrow(_cursor, "neckInclinationDeg");
          final int _cursorIndexOfHeadTiltDeg = CursorUtil.getColumnIndexOrThrow(_cursor, "headTiltDeg");
          final int _cursorIndexOfShoulderSymmetryPx = CursorUtil.getColumnIndexOrThrow(_cursor, "shoulderSymmetryPx");
          final int _cursorIndexOfState = CursorUtil.getColumnIndexOrThrow(_cursor, "state");
          final List<PostureSample> _result = new ArrayList<PostureSample>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final PostureSample _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpSessionId;
            _tmpSessionId = _cursor.getString(_cursorIndexOfSessionId);
            final long _tmpTimestamp;
            _tmpTimestamp = _cursor.getLong(_cursorIndexOfTimestamp);
            final float _tmpForwardHeadOffsetPx;
            _tmpForwardHeadOffsetPx = _cursor.getFloat(_cursorIndexOfForwardHeadOffsetPx);
            final float _tmpNeckInclinationDeg;
            _tmpNeckInclinationDeg = _cursor.getFloat(_cursorIndexOfNeckInclinationDeg);
            final float _tmpHeadTiltDeg;
            _tmpHeadTiltDeg = _cursor.getFloat(_cursorIndexOfHeadTiltDeg);
            final float _tmpShoulderSymmetryPx;
            _tmpShoulderSymmetryPx = _cursor.getFloat(_cursorIndexOfShoulderSymmetryPx);
            final String _tmpState;
            _tmpState = _cursor.getString(_cursorIndexOfState);
            _item = new PostureSample(_tmpId,_tmpSessionId,_tmpTimestamp,_tmpForwardHeadOffsetPx,_tmpNeckInclinationDeg,_tmpHeadTiltDeg,_tmpShoulderSymmetryPx,_tmpState);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public Flow<List<Session>> getAllSessions() {
    final String _sql = "SELECT * FROM sessions ORDER BY startTime DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"sessions"}, new Callable<List<Session>>() {
      @Override
      @NonNull
      public List<Session> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfSessionId = CursorUtil.getColumnIndexOrThrow(_cursor, "sessionId");
          final int _cursorIndexOfStartTime = CursorUtil.getColumnIndexOrThrow(_cursor, "startTime");
          final int _cursorIndexOfEndTime = CursorUtil.getColumnIndexOrThrow(_cursor, "endTime");
          final int _cursorIndexOfGoodPercent = CursorUtil.getColumnIndexOrThrow(_cursor, "goodPercent");
          final int _cursorIndexOfTotalAlerts = CursorUtil.getColumnIndexOrThrow(_cursor, "totalAlerts");
          final List<Session> _result = new ArrayList<Session>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final Session _item;
            final String _tmpSessionId;
            _tmpSessionId = _cursor.getString(_cursorIndexOfSessionId);
            final long _tmpStartTime;
            _tmpStartTime = _cursor.getLong(_cursorIndexOfStartTime);
            final Long _tmpEndTime;
            if (_cursor.isNull(_cursorIndexOfEndTime)) {
              _tmpEndTime = null;
            } else {
              _tmpEndTime = _cursor.getLong(_cursorIndexOfEndTime);
            }
            final Float _tmpGoodPercent;
            if (_cursor.isNull(_cursorIndexOfGoodPercent)) {
              _tmpGoodPercent = null;
            } else {
              _tmpGoodPercent = _cursor.getFloat(_cursorIndexOfGoodPercent);
            }
            final Integer _tmpTotalAlerts;
            if (_cursor.isNull(_cursorIndexOfTotalAlerts)) {
              _tmpTotalAlerts = null;
            } else {
              _tmpTotalAlerts = _cursor.getInt(_cursorIndexOfTotalAlerts);
            }
            _item = new Session(_tmpSessionId,_tmpStartTime,_tmpEndTime,_tmpGoodPercent,_tmpTotalAlerts);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public Object getSession(final String sessionId,
      final Continuation<? super Session> $completion) {
    final String _sql = "SELECT * FROM sessions WHERE sessionId = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, sessionId);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<Session>() {
      @Override
      @Nullable
      public Session call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfSessionId = CursorUtil.getColumnIndexOrThrow(_cursor, "sessionId");
          final int _cursorIndexOfStartTime = CursorUtil.getColumnIndexOrThrow(_cursor, "startTime");
          final int _cursorIndexOfEndTime = CursorUtil.getColumnIndexOrThrow(_cursor, "endTime");
          final int _cursorIndexOfGoodPercent = CursorUtil.getColumnIndexOrThrow(_cursor, "goodPercent");
          final int _cursorIndexOfTotalAlerts = CursorUtil.getColumnIndexOrThrow(_cursor, "totalAlerts");
          final Session _result;
          if (_cursor.moveToFirst()) {
            final String _tmpSessionId;
            _tmpSessionId = _cursor.getString(_cursorIndexOfSessionId);
            final long _tmpStartTime;
            _tmpStartTime = _cursor.getLong(_cursorIndexOfStartTime);
            final Long _tmpEndTime;
            if (_cursor.isNull(_cursorIndexOfEndTime)) {
              _tmpEndTime = null;
            } else {
              _tmpEndTime = _cursor.getLong(_cursorIndexOfEndTime);
            }
            final Float _tmpGoodPercent;
            if (_cursor.isNull(_cursorIndexOfGoodPercent)) {
              _tmpGoodPercent = null;
            } else {
              _tmpGoodPercent = _cursor.getFloat(_cursorIndexOfGoodPercent);
            }
            final Integer _tmpTotalAlerts;
            if (_cursor.isNull(_cursorIndexOfTotalAlerts)) {
              _tmpTotalAlerts = null;
            } else {
              _tmpTotalAlerts = _cursor.getInt(_cursorIndexOfTotalAlerts);
            }
            _result = new Session(_tmpSessionId,_tmpStartTime,_tmpEndTime,_tmpGoodPercent,_tmpTotalAlerts);
          } else {
            _result = null;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object getGoodCount(final String sessionId,
      final Continuation<? super Integer> $completion) {
    final String _sql = "SELECT COUNT(*) FROM posture_samples WHERE sessionId = ? AND state = 'GOOD'";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, sessionId);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<Integer>() {
      @Override
      @NonNull
      public Integer call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final Integer _result;
          if (_cursor.moveToFirst()) {
            final int _tmp;
            _tmp = _cursor.getInt(0);
            _result = _tmp;
          } else {
            _result = 0;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object getTotalCount(final String sessionId,
      final Continuation<? super Integer> $completion) {
    final String _sql = "SELECT COUNT(*) FROM posture_samples WHERE sessionId = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, sessionId);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<Integer>() {
      @Override
      @NonNull
      public Integer call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final Integer _result;
          if (_cursor.moveToFirst()) {
            final int _tmp;
            _tmp = _cursor.getInt(0);
            _result = _tmp;
          } else {
            _result = 0;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object getAlertCount(final String sessionId,
      final Continuation<? super Integer> $completion) {
    final String _sql = "SELECT COUNT(*) FROM posture_samples WHERE sessionId = ? AND state IN ('WARNING', 'BAD')";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, sessionId);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<Integer>() {
      @Override
      @NonNull
      public Integer call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final Integer _result;
          if (_cursor.moveToFirst()) {
            final int _tmp;
            _tmp = _cursor.getInt(0);
            _result = _tmp;
          } else {
            _result = 0;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object getBadCount(final String sessionId,
      final Continuation<? super Integer> $completion) {
    final String _sql = "SELECT COUNT(*) FROM posture_samples WHERE sessionId = ? AND state = 'BAD'";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, sessionId);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<Integer>() {
      @Override
      @NonNull
      public Integer call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final Integer _result;
          if (_cursor.moveToFirst()) {
            final int _tmp;
            _tmp = _cursor.getInt(0);
            _result = _tmp;
          } else {
            _result = 0;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object getWarningCount(final String sessionId,
      final Continuation<? super Integer> $completion) {
    final String _sql = "SELECT COUNT(*) FROM posture_samples WHERE sessionId = ? AND state = 'WARNING'";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, sessionId);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<Integer>() {
      @Override
      @NonNull
      public Integer call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final Integer _result;
          if (_cursor.moveToFirst()) {
            final int _tmp;
            _tmp = _cursor.getInt(0);
            _result = _tmp;
          } else {
            _result = 0;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}
