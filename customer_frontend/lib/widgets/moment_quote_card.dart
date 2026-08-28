import 'package:flutter/material.dart';

import '../moment_models.dart';
import '../utils/moment_time_formatter.dart';
import '../services/api_client.dart';

class MomentQuoteCard extends StatelessWidget {
  const MomentQuoteCard({super.key, required this.quote, this.onTap});

  final MomentQuote quote;
  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) {
    final isComment = quote.type == 'COMMENT';
    return InkWell(
      onTap: onTap,
      child: Container(
        margin: const EdgeInsets.only(top: 12),
        padding: const EdgeInsets.fromLTRB(12, 10, 8, 10),
        decoration: const BoxDecoration(
          color: Colors.white,
          border: Border(left: BorderSide(color: Color(0xFFDADDE1), width: 3)),
        ),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            CircleAvatar(
              radius: 17,
              backgroundColor: const Color(0xFFFFE9D5),
              backgroundImage: quote.author?.avatarUrl?.isNotEmpty == true
                  ? NetworkImage(
                      resolveCustomerImageUrl(quote.author!.avatarUrl!),
                    )
                  : null,
              child: quote.author?.avatarUrl?.isNotEmpty == true
                  ? null
                  : const Icon(Icons.person_outline_rounded, size: 17),
            ),
            const SizedBox(width: 9),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      Expanded(
                        child: Text(
                          isComment
                              ? (quote.author?.nickname ?? '用户')
                              : (quote.momentTitle.isEmpty
                                    ? '动态'
                                    : quote.momentTitle),
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: const TextStyle(
                            fontSize: 13,
                            fontWeight: FontWeight.w700,
                          ),
                        ),
                      ),
                      Text(
                        formatMomentTime(quote.createdAt),
                        style: const TextStyle(
                          fontSize: 10.5,
                          color: Color(0xFF989DA4),
                        ),
                      ),
                    ],
                  ),
                  if (!isComment && quote.author != null)
                    Padding(
                      padding: const EdgeInsets.only(top: 2),
                      child: Text(
                        quote.author!.nickname,
                        style: const TextStyle(
                          fontSize: 11,
                          color: Color(0xFF858A91),
                        ),
                      ),
                    ),
                  const SizedBox(height: 5),
                  Text(
                    quote.content.isEmpty
                        ? (quote.unavailableReason ?? '引用内容暂不可用')
                        : quote.content,
                    maxLines: isComment ? 2 : 3,
                    overflow: TextOverflow.ellipsis,
                    style: TextStyle(
                      fontSize: 12.5,
                      height: 1.45,
                      color: quote.available
                          ? const Color(0xFF555A61)
                          : const Color(0xFF969BA2),
                    ),
                  ),
                  if (isComment && quote.momentTitle.isNotEmpty)
                    Padding(
                      padding: const EdgeInsets.only(top: 5),
                      child: Text(
                        '来自：${quote.momentTitle}',
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: const TextStyle(
                          fontSize: 10.5,
                          color: Color(0xFFA0A4AA),
                        ),
                      ),
                    ),
                ],
              ),
            ),
            if (onTap != null)
              const Padding(
                padding: EdgeInsets.only(top: 12),
                child: Icon(
                  Icons.chevron_right_rounded,
                  size: 19,
                  color: Color(0xFFA2A6AC),
                ),
              ),
          ],
        ),
      ),
    );
  }
}
