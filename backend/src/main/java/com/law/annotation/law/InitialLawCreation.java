package com.law.annotation.law;

import com.law.annotation.version.ContentVersionDocument;

public record InitialLawCreation(LawDocument law, ContentVersionDocument contentVersion) {
}
