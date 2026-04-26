import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:flutter_varsom/main.dart';

void main() {
  testWidgets('shows Varsom app title', (tester) async {
    await tester.pumpWidget(const VarsomApp());
    expect(find.text('Skredvarsel'), findsOneWidget);
    expect(find.byIcon(Icons.map), findsOneWidget);
  });
}
