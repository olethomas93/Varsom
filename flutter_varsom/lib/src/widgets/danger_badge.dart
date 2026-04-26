import 'package:flutter/material.dart';

import '../theme.dart';

class DangerBadge extends StatelessWidget {
  const DangerBadge({
    required this.level,
    this.size = 52,
    super.key,
  });

  final String level;
  final double size;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: size,
      height: size,
      alignment: Alignment.center,
      decoration: BoxDecoration(
        color: dangerColor(level),
        borderRadius: BorderRadius.circular(8),
      ),
      child: Text(
        level == '0' ? '-' : level,
        style: Theme.of(context).textTheme.headlineSmall?.copyWith(
              color: Colors.white,
              fontWeight: FontWeight.w800,
            ),
      ),
    );
  }
}
