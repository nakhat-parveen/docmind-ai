package com.docmind.qa;

import java.util.List;

public record Answer(String answer, List<Citation> citations) {
}
