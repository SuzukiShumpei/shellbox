# adbコマンド Firebase ログを収集

## 概要

`FA`タグでFirebaseログが出力されているので、Debug Viewで確認できるレベルの情報をログで確認。スクリーンイベント時は以下のようなログが確認できる。
```
Logging event: origin=app,name=screen_view(_vs),params=Bundle[{ga_event_origin(_o)=app, engagement_time_msec(_et)=19, ga_previous_class(_pc)=CashCardActivity, ga_previous_id(_pi)=813494096113756619, ga_screen_class(_sc)=CashCardTopScreen, ga_screen_id(_si)=813494096113756623, ga_screen(_sn)=RS4600M01, session_id=1776921799}]
```

## 事前準備

- エミュレーターターを起動しておく

## 使い方

1. 実行するだけでOK。
