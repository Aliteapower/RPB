-- Keep template constants ASCII-only so Windows psql client encoding cannot corrupt matching.
-- template_md5 values are md5(UTF8(normalized known default templates)); new_template is UTF-8 hex.
with old_default_templates(template_md5) as (
    values
        ('0330ca8282c4ce3c608ebf3fa23c9c4a'),
        ('470d74c4357fd9e699ad6556e125d7a7')
),
templates as (
    select convert_from(decode(
        'e5b08ae695ace79a84207b7b636f6e746163744e616d657d7d207b7b677565737453616c75746174696f6e7d7defbc8c0a0ae6849fe8b0a2e682a8e9' ||
        '8089e68ba9207b7b73746f72654e616d657d7de38082e68891e4bbace5be88e88da3e5b9b8e59cb0e7a1aee8aea4e682a8e79a84e9a284e8aea2e5ae' ||
        '89e68e92efbc8ce585b7e4bd93e4bfa1e681afe5a682e4b88befbc9a0a0ae9a284e8aea2e7bc96e58fb7efbc9a7b7b7265736572766174696f6e4e6f' ||
        '7d7d0a0ae697a5e69c9fefbc9a7b7b7265736572766174696f6e446174657d7d0a0ae697b6e997b4efbc9a7b7b7265736572766174696f6e54696d65' ||
        '7d7d0a0ae4babae695b0efbc9a7b7b706172747953697a657d7de4bd8de68890e4baba0a0ae6a18ce4bd8defbc9a7b7b7461626c65436f64657d7d20' ||
        '28e5b7b2e9a284e79599290a0ae9a284e79599e697b6e997b4efbc9ae4b8bae4bf9de8af81e68980e69c89e5aebee5aea2e79a84e794a8e9a490e4bd' ||
        '93e9aa8cefbc8ce68891e4bbace5b086e4b8bae682a8e4bf9de79599e5baa7e4bd8d207b7b686f6c644d696e757465737d7de58886e9929fe38082e8' ||
        '8ba5e8b685e8bf87e4bf9de79599e697b6e997b4efbc8ce5baa7e4bd8de58fafe883bde8a2abe58f96e6b688efbc8ce695ace8afb7e8b085e8a7a3e3' ||
        '80820a0ae588b0e5ba97e68f90e7a4baefbc9a7b7b6172726976616c4e6f74657d7d0a0ae997a8e5ba97e59cb0e59d80efbc9a7b7b73746f72654164' ||
        '64726573737d7d0a0ae88194e7b3bbe794b5e8af9defbc9a7b7b73746f726550686f6e657d7d0a0ae5a682e99c80e4bfaee694b9e68896e58f96e6b6' ||
        '88efbc8ce8afb7e887b3e5b091e68f90e5898d203220e5b08fe697b6e88194e7b3bbe997a8e5ba97e380820a0ae69c9fe5be85e4b8bae682a8e5a589' ||
        'e4b88ae4b880e59cbae591b3e895bee79b9be5aeb4efbc810a0ae9a1bae9a282e697b6e7a5baefbc8c0a7b7b73746f72654e616d657d7d20e9a284e8' ||
        'aea2e983a8',
        'hex'
    ), 'UTF8') as new_template
)
update platform_reservation_share_template_seeds seed
set template_text = templates.new_template,
    updated_at = now(),
    version = seed.version + 1
from templates
where seed.seed_key = 'restaurant_reservation_confirmation_v1'
  and seed.deleted_at is null
  and md5(convert_to(replace(replace(btrim(seed.template_text), chr(13) || chr(10), chr(10)), chr(13), chr(10)), 'UTF8'))
      in (select template_md5 from old_default_templates)
  and seed.template_text is distinct from templates.new_template;

with old_default_templates(template_md5) as (
    values
        ('0330ca8282c4ce3c608ebf3fa23c9c4a'),
        ('470d74c4357fd9e699ad6556e125d7a7')
),
templates as (
    select convert_from(decode(
        'e5b08ae695ace79a84207b7b636f6e746163744e616d657d7d207b7b677565737453616c75746174696f6e7d7defbc8c0a0ae6849fe8b0a2e682a8e9' ||
        '8089e68ba9207b7b73746f72654e616d657d7de38082e68891e4bbace5be88e88da3e5b9b8e59cb0e7a1aee8aea4e682a8e79a84e9a284e8aea2e5ae' ||
        '89e68e92efbc8ce585b7e4bd93e4bfa1e681afe5a682e4b88befbc9a0a0ae9a284e8aea2e7bc96e58fb7efbc9a7b7b7265736572766174696f6e4e6f' ||
        '7d7d0a0ae697a5e69c9fefbc9a7b7b7265736572766174696f6e446174657d7d0a0ae697b6e997b4efbc9a7b7b7265736572766174696f6e54696d65' ||
        '7d7d0a0ae4babae695b0efbc9a7b7b706172747953697a657d7de4bd8de68890e4baba0a0ae6a18ce4bd8defbc9a7b7b7461626c65436f64657d7d20' ||
        '28e5b7b2e9a284e79599290a0ae9a284e79599e697b6e997b4efbc9ae4b8bae4bf9de8af81e68980e69c89e5aebee5aea2e79a84e794a8e9a490e4bd' ||
        '93e9aa8cefbc8ce68891e4bbace5b086e4b8bae682a8e4bf9de79599e5baa7e4bd8d207b7b686f6c644d696e757465737d7de58886e9929fe38082e8' ||
        '8ba5e8b685e8bf87e4bf9de79599e697b6e997b4efbc8ce5baa7e4bd8de58fafe883bde8a2abe58f96e6b688efbc8ce695ace8afb7e8b085e8a7a3e3' ||
        '80820a0ae588b0e5ba97e68f90e7a4baefbc9a7b7b6172726976616c4e6f74657d7d0a0ae997a8e5ba97e59cb0e59d80efbc9a7b7b73746f72654164' ||
        '64726573737d7d0a0ae88194e7b3bbe794b5e8af9defbc9a7b7b73746f726550686f6e657d7d0a0ae5a682e99c80e4bfaee694b9e68896e58f96e6b6' ||
        '88efbc8ce8afb7e887b3e5b091e68f90e5898d203220e5b08fe697b6e88194e7b3bbe997a8e5ba97e380820a0ae69c9fe5be85e4b8bae682a8e5a589' ||
        'e4b88ae4b880e59cbae591b3e895bee79b9be5aeb4efbc810a0ae9a1bae9a282e697b6e7a5baefbc8c0a7b7b73746f72654e616d657d7d20e9a284e8' ||
        'aea2e983a8',
        'hex'
    ), 'UTF8') as new_template
)
update stores
set reservation_share_template = templates.new_template,
    updated_at = now(),
    version = stores.version + 1
from templates
where stores.deleted_at is null
  and md5(convert_to(replace(replace(btrim(stores.reservation_share_template), chr(13) || chr(10), chr(10)), chr(13), chr(10)), 'UTF8'))
      in (select template_md5 from old_default_templates)
  and stores.reservation_share_template is distinct from templates.new_template;
