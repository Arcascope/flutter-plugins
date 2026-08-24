import 'package:flutter/foundation.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:flutter/services.dart';
import 'package:health/health.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  HealthDataPoint sleepPoint({
    required String uuid,
    required HealthDataType type,
    DateTime? start,
    DateTime? end,
  }) {
    start ??= DateTime.utc(2026, 8, 20, 3);
    end ??= start.add(const Duration(minutes: 17, seconds: 30));
    return HealthDataPoint.fromHealthDataPoint(type, <String, dynamic>{
      'uuid': uuid,
      'value': switch (type) {
        HealthDataType.SLEEP_ASLEEP_CORE => 3,
        HealthDataType.SLEEP_ASLEEP_DEEP => 4,
        HealthDataType.SLEEP_ASLEEP_REM => 5,
        _ => 1,
      },
      'date_from': start.millisecondsSinceEpoch,
      'date_to': end.millisecondsSinceEpoch,
      'source_device_id': 'watch-device',
      'source_id': 'com.apple.health.test',
      'source_name': 'Test Apple Watch',
      'is_manual_entry': false,
    });
  }

  test('Apple-specific sleep stages expose duration rather than category', () {
    for (final type in <HealthDataType>[
      HealthDataType.SLEEP_ASLEEP_CORE,
      HealthDataType.SLEEP_ASLEEP_DEEP,
      HealthDataType.SLEEP_ASLEEP_REM,
    ]) {
      final point = sleepPoint(uuid: type.name, type: type);
      expect((point.value as NumericHealthValue).numericValue, 17.5);
      expect(point.sourceDeviceId, 'watch-device');
    }
  });

  test('HealthKit UUID survives JSON serialization', () {
    final point = sleepPoint(
      uuid: 'healthkit-sample-id',
      type: HealthDataType.SLEEP_ASLEEP_DEEP,
    );

    final json = point.toJson()
      ..['value'] = (point.value as NumericHealthValue).toJson();
    expect(json['uuid'], 'healthkit-sample-id');
    expect(
      HealthDataPoint.fromJson(json).uuid,
      'healthkit-sample-id',
    );
  });

  test('removeDuplicates collapses aliases with the same HealthKit UUID', () {
    final generic = sleepPoint(
      uuid: 'same-native-sample',
      type: HealthDataType.SLEEP_DEEP,
    );
    final appleSpecific = sleepPoint(
      uuid: 'same-native-sample',
      type: HealthDataType.SLEEP_ASLEEP_DEEP,
    );

    expect(Health().removeDuplicates([generic, appleSpecific]), [generic]);
  });

  test('all iOS sleep aliases use one native query and canonical types',
      () async {
    debugDefaultTargetPlatformOverride = TargetPlatform.iOS;
    addTearDown(() => debugDefaultTargetPlatformOverride = null);
    const channel = MethodChannel('flutter_health');
    final calls = <MethodCall>[];
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(channel, (call) async {
      calls.add(call);
      expect(call.method, 'getSleepData');
      return <Map<String, dynamic>>[
        for (var value = 0; value <= 5; value++)
          <String, dynamic>{
            'uuid': 'sleep-$value',
            'value': value,
            'date_from':
                DateTime.utc(2026, 8, 20, value).millisecondsSinceEpoch,
            'date_to':
                DateTime.utc(2026, 8, 20, value, 10).millisecondsSinceEpoch,
            'source_device_id': 'watch-device',
            'source_id': 'com.apple.health.test',
            'source_name': 'Test Apple Watch',
            'is_manual_entry': false,
          },
      ];
    });
    addTearDown(() => TestDefaultBinaryMessengerBinding
        .instance.defaultBinaryMessenger
        .setMockMethodCallHandler(channel, null));

    final points = await Health().getHealthDataFromTypes(
      types: const <HealthDataType>[
        HealthDataType.SLEEP_IN_BED,
        HealthDataType.SLEEP_ASLEEP,
        HealthDataType.SLEEP_AWAKE,
        HealthDataType.SLEEP_DEEP,
        HealthDataType.SLEEP_REM,
        HealthDataType.SLEEP_ASLEEP_CORE,
        HealthDataType.SLEEP_ASLEEP_DEEP,
        HealthDataType.SLEEP_ASLEEP_REM,
      ],
      startTime: DateTime.utc(2026, 8, 20),
      endTime: DateTime.utc(2026, 8, 21),
    );

    expect(calls, hasLength(1));
    expect(points.map((point) => point.type), <HealthDataType>[
      HealthDataType.SLEEP_IN_BED,
      HealthDataType.SLEEP_ASLEEP,
      HealthDataType.SLEEP_AWAKE,
      HealthDataType.SLEEP_ASLEEP_CORE,
      HealthDataType.SLEEP_ASLEEP_DEEP,
      HealthDataType.SLEEP_ASLEEP_REM,
    ]);
    expect(
      points.map((point) => (point.value as NumericHealthValue).numericValue),
      everyElement(10),
    );
  });
}
